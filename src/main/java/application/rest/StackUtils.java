package application.rest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.yaml.snakeyaml.Yaml;

import com.github.zafarkhaja.semver.Version;
import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

import io.kabanero.v1alpha2.client.apis.KabaneroApi;
import io.kabanero.v1alpha2.models.Kabanero;
import io.kabanero.v1alpha2.models.KabaneroList;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksGitRelease;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksHttps;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksPipelines;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksRepositories;
import io.kabanero.v1alpha2.models.Stack;
import io.kabanero.v1alpha2.models.StackList;
import io.kabanero.v1alpha2.models.StackSpec;
import io.kabanero.v1alpha2.models.StackSpecImages;
import io.kabanero.v1alpha2.models.StackSpecVersions;
import io.kabanero.v1alpha2.models.StackStatus;
import io.kabanero.v1alpha2.models.StackStatusVersions;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ContainerStatus;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1PodStatus;

public class StackUtils {
	
	// this value needs to start out as true, otherwise the liveness probe can't come up 
	// as healthy initially
	public static boolean readGitSuccess=true;
	static HashMap<String, String> containerRegistries = new HashMap<String, String>();
	static {
		containerRegistries.put("docker.io", "registry.hub.docker.com");
	}
	
	public static Comparator<Map<String, String>> mapComparator = new Comparator<Map<String, String>>() {
	    public int compare(Map<String, String> m1, Map<String, String> m2) {
	        return m1.get("name").compareTo(m2.get("name"));
	    }
	};
	
	public static Comparator<Map<String, String>> mapComparator2 = new Comparator<Map<String, String>>() {
	    public int compare(Map<String, String> m1, Map<String, String> m2) {
	    	Version v1 = Version.valueOf(m1.get("version"));
	    	Version v2 = Version.valueOf(m2.get("version"));
	    	return v1.compareTo(v2);
	    }
	};
	
	public static String getImageDigestFromRegistry(String stackName, String versionNumber, String namespace, String crNamespace, String containerRegistryURL) throws ApiException, IOException, KeyManagementException, NoSuchAlgorithmException {
		System.out.println("containerRegistryURL="+containerRegistryURL);
		Map<String, String> m = null;
		boolean canLocaterCRSecret = true;
		try {
			m = KubeUtils.getUserAndPasswordFromSecret(namespace, containerRegistryURL);
		}  catch (ApiException e) {
			String exMsg = e.getMessage();
			if (exMsg.contains("Could not retrieve kubernetes secret")) {
				canLocaterCRSecret = false;
			}
		}
		System.out.println("stackName="+stackName);
		System.out.println("versionNumber="+versionNumber);
		System.out.println("namespace="+namespace);
		System.out.println("crNamespace="+crNamespace);

		String digest=null;
		String[] commandToRun = null;
		if (canLocaterCRSecret) {
			String parm1 = "inspect";
			String parm2 = "--creds";
			String parm3 = (String) m.get("user")+":"+(String) m.get("password");
			String parm4 = "docker://"+containerRegistryURL+"/"+crNamespace+"/"+stackName+":"+versionNumber;
			String[] command = {"/usr/local/bin/skopeo",parm1,parm2, parm3, parm4};
			commandToRun = command;
		} else {
			String parm1 = "inspect";
			String parm2 = "docker://"+containerRegistryURL+"/"+crNamespace+"/"+stackName+":"+versionNumber;
			String[] command = {"/usr/local/bin/skopeo",parm1,parm2};
			commandToRun = command;
		}
		
		Process process = Runtime.getRuntime().exec(commandToRun);
		Scanner kb = new Scanner(process.getInputStream());
		StringBuilder sb = new StringBuilder();
		for(;kb.hasNext();) {
			sb.append(kb.next());
		}
		kb.close();
		String result = sb.toString();
		if (result!=null) {
			if (result.contains("manifest unknown")||result.length()==0) {
				return "image not found in container registry";
			}
			System.out.println("result from skopeo:  "+result);
			JSONObject jo = JSONObject.parse(result);
			digest = (String) jo.get("Digest");
			digest = digest.substring(digest.lastIndexOf(":")+1);
			return digest;
		}
		return null;
	}
	
	

	private static Map readYaml(String response) {
		Yaml yaml = new Yaml();
		Map<String, Object> obj = null;
		try {
			InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
			obj = yaml.load(inputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return obj;
	}
	
	private static boolean pause() {
        // Sleep for half a second before next try
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Something woke us up, most probably process is exiting.
            // Just break out of the loop to report the last DB exception.
            return true;
        }
        return false;
    }
	
	

	public static String getWithREST(String url, String user, String pw, String contentType) throws KeyManagementException, NoSuchAlgorithmException {
		HttpClientBuilder clientBuilder = HttpClients.custom();
		CredentialsProvider credsProvider = new BasicCredentialsProvider();

		if (user!=null) {
			credsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(user, pw));
		}

		clientBuilder.setDefaultCredentialsProvider(credsProvider);

		SSLContext sslContext = SSLContexts.custom()
				.useTLS()
				.build();

		SSLConnectionSocketFactory f = new SSLConnectionSocketFactory(
				sslContext,
				new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"},   
				null,
				SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
		HttpClient client = clientBuilder.create().setSSLSocketFactory(f).build();
		System.out.print("REST get with URL: "+url);
		HttpGet request = new HttpGet(url);
		request.addHeader("accept", "application/"+contentType);

		if (user==null) { 
			request.addHeader("Authorization", pw);
		}

		// add request header

		HttpResponse response = null;
		IOException savedEx=null;
		int retries = 0;
		for (; retries < 10; retries++) {
			try {
				response = client.execute(request);
				readGitSuccess=true;
				break;
			} catch (IOException e) {
				e.printStackTrace();
				savedEx=e;
			}
			if (pause()) {
				break;
			}
		}
		if (retries >= 10) {
			readGitSuccess=false;
			throw new RuntimeException("Exception connecting or executing REST command to Git url: "+url, savedEx);
		}
		System.out.println("response.toString(): "+ response.toString());

		System.out.println("Response Code : " + response.getStatusLine().getStatusCode());
		if (response.getStatusLine().getStatusCode()==429) {
			return "HTTP Code 429: GitHub retry limit exceeded, please try again in 2 minutes";
		}
		BufferedReader rd = null;
		try {
			rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		} catch (IllegalStateException | IOException e) {
			e.printStackTrace();
		}
		StringBuffer result = new StringBuffer();
		String line = "";

		try {
			while ((line = rd.readLine()) != null) {
				result.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result.toString();
	}
	
	public static String getImage(String namespace) throws Exception {
		String image=null;
		ApiClient apiClient = KubeUtils.getApiClient();
		CoreV1Api api = new CoreV1Api();
		V1PodList list = api.listNamespacedPod(namespace, false, null, null, "", "", 30, null, 60, false);

		for (V1Pod item : list.getItems()) {
			if (item.getMetadata().getName().contains("kabanero-cli")) {
				V1PodStatus status = (V1PodStatus)item.getStatus();
				List<V1ContainerStatus> containerStatuses =  status.getContainerStatuses();

				for (V1ContainerStatus containerStatus : containerStatuses) {
					if ("kabanero-cli".contentEquals(containerStatus.getName())) {
						image = containerStatus.getImage();
					}
				}


			}
		}
		image = image.replace("docker.io/", "");
		return image;
	}
	
	

	public static Kabanero getKabaneroForNamespace(String namespace) {
		String url = null;
		try {
			ApiClient apiClient = KubeUtils.getApiClient();
			KabaneroApi api = new KabaneroApi(apiClient);
			KabaneroList kabaneros = api.listKabaneros(namespace, null, null, null);
			List<Kabanero> kabaneroList = kabaneros.getItems();
			if (kabaneroList.size() > 0) {
				return kabaneroList.get(0);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	public static long getAssetId(String url, String org, String project, String release, String namespace, String assetName) throws ApiException, IOException, KeyManagementException, NoSuchAlgorithmException {
		
		String get_release_url = "https://api."+url+"/repos/"+org+"/"+project+"/releases/tags/"+release;
		
		String response = getWithREST(get_release_url, null, "token "+KubeUtils.getSecret(namespace, url),"json");
		
		JSONObject asset_metadata=new JSONObject();
		
		asset_metadata = JSONObject.parse(response);
		
		JSONArray assets = (JSONArray) asset_metadata.get("assets");
		long asset_id=0;
		
		for (Object obj:assets) {
			JSONObject assetObj = (JSONObject) obj;
			if (assetName.contentEquals((String)assetObj.get("name"))) {
				asset_id=(Long)assetObj.get("id");
			}
		}
		return asset_id;
	}
	
	
	public static List getStackFromGIT(String user, String pw, KabaneroSpecStacksRepositories r,String namespace) throws Exception {
		String response = null;
		String url = null;
		KabaneroSpecStacksHttps kabaneroSpecStacksHttps = r.getHttps();
		if (kabaneroSpecStacksHttps != null) {
			url = kabaneroSpecStacksHttps.getUrl();
		}
		System.out.println("public git url="+url);
		try {
			if (url == null) {
				KabaneroSpecStacksGitRelease kabaneroSpecStacksGitRelease = r.getGitRelease();
				System.out.println("kabaneroSpecStacksGitRelease="+kabaneroSpecStacksGitRelease);
				if (kabaneroSpecStacksGitRelease == null) {
					System.out.println("No repository URL specified");
					throw new RuntimeException("No repository URL specified");
				}
				String org, project, release;
				String secret_url = url = kabaneroSpecStacksGitRelease.getHostname();
				System.out.println("GHE git url="+url);
				org = kabaneroSpecStacksGitRelease.getOrganization();
				project = kabaneroSpecStacksGitRelease.getProject();
				release = kabaneroSpecStacksGitRelease.getRelease();
				
				long asset_id=getAssetId(url, org, project, release, namespace, kabaneroSpecStacksGitRelease.getAssetName());
				
				String get_asset_url = "https://"+url+"/api/v3/repos/"+org+"/"+project+"/releases/assets/"+asset_id;
				
				response = getWithREST(get_asset_url, null, KubeUtils.getSecret(namespace,secret_url),"octet-stream");
			} else {
				System.out.println("in getStackFromGIT, reading from github public index: "+url);
				response = getWithREST(url, user, pw, "yaml");

			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		if (response != null) {
			if (response.contains("HTTP Code 429:")) {
				ArrayList<String> list = new ArrayList();
				list.add(response);
				return list;
			}
		}
		ArrayList<Map> list = null;
		try {
			Map m = readYaml(response);
			list = (ArrayList<Map>) m.get("stacks");
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (Map map: list) {
			map.put("reponame", r.getName());
		}
		return list;
	}
		

	public static List streamLineMasterMap(List<Map> list) {
		ArrayList aList = new ArrayList();
		for (Map map : list) {
			String name = (String) map.get("id");
			String version = (String) map.get("version");
			String reponame = (String) map.get("reponame");
			String imageStr = (String) map.get("image");
			List<StackSpecImages> stackSpecImages = new ArrayList<StackSpecImages>();
			if (imageStr==null) {
				List<Map> images = (List<Map>) map.get("images");
				for (Map image: images) {
					StackSpecImages stackSpecImage = new StackSpecImages();
					stackSpecImage.setImage((String) image.get("image"));
					stackSpecImages.add(stackSpecImage);
				}
			} else {
				StackSpecImages stackSpecImage = new StackSpecImages();
				stackSpecImage.setImage(imageStr);
				stackSpecImages.add(stackSpecImage);
			}
			HashMap outMap = new HashMap();
			outMap.put("name", name);
			outMap.put("version", version);
			outMap.put("images", stackSpecImages);
			outMap.put("reponame", reponame);
			aList.add(outMap);
		}
		return aList;
	}
	
	public static HashMap<String,String> getKabStackDigest(Stack s, String versionToFind) {
		String digest=null,imageName=null;
		HashMap<String,String> imageMetaData = new HashMap<String,String>();
		
		System.out.println("getting digest for: "+s.getSpec().getName()+", versionToFind: "+versionToFind);
		
		List<StackStatusVersions> versions=s.getStatus().getVersions();
		// note eventually we may have multiple images, potentially for multiple architectures
		for (StackStatusVersions version:versions) {
			if (version.getImages()!=null) {
				digest=version.getImages().get(0).getDigest().getActivation();
				imageName=version.getImages().get(0).getImage();
			}
		}
		
		imageMetaData.put("digest",digest);
		imageMetaData.put("imageName",imageName);
		
		
		return imageMetaData;
	}
	
	public static String getRepoName(List curatedStacks, String name, String version) {
		String repoName=null;
		for (Object obj:curatedStacks) {
			Map stack = (Map)obj;
			String nameStr = (String) stack.get("id");
			String nameStr2 = (String) stack.get("name");
			String versionStr = (String) stack.get("version");
			if ((nameStr.contentEquals(name) || nameStr2.contentEquals(name)) && versionStr.contentEquals(version)) {
				repoName = (String) stack.get("reponame");
			}
		}
		return repoName;
	}
	
	public static String digestCheck(String kabDigest, String imageDigest, String status) {
		String digestCheck="mismatched";
		if (kabDigest!=null && imageDigest!=null) {
			if (kabDigest.contentEquals(imageDigest)) {
				digestCheck="matched";
			} else if (imageDigest.contains("not found in container registry")) {
				digestCheck = imageDigest;
			}
		} else {
			System.out.println("Could not find one of the digests.  Kab digest="+kabDigest+", imageDigest="+imageDigest);
			digestCheck="unknown";
		}
		if (status!=null) {
			if (status.contains("active")) {
				status=status.substring(0, 6);  
				if (!"active".contentEquals(status)) {
					digestCheck = "NA";
				}
			} else {
				digestCheck = "NA";
			}
		} else {
			digestCheck = "NA";
		}
		return digestCheck;
	}
	

	
	public static List allStacks(StackList fromKabanero, String namespace, List curatedStacks) throws Exception {
		ArrayList<Map> allStacks = new ArrayList<Map>();
		try {
			for (Stack s : fromKabanero.getItems()) {
				HashMap allMap = new HashMap();
				String name = s.getMetadata().getName();
				name = name.trim();
				List<StackStatusVersions> versions = s.getStatus().getVersions();
				List status = new ArrayList<Map>();
				for (StackStatusVersions stackStatusVersion : versions) {
					HashMap<String,String> versionMap = new HashMap<String,String>();
					String statusStr = stackStatusVersion.getStatus();
					if ("inactive".contentEquals(statusStr)) {
						if (isStatusPending(s, stackStatusVersion.getVersion())) {
							statusStr = "active pending";
						}
					}
					versionMap.put("status", statusStr);
					String versionNum=stackStatusVersion.getVersion();
					versionMap.put("version", versionNum);
					
					Map imageMap = getKabStackDigest(s, versionNum);
					
					String kabDigest = null, image = null, imageDigest=null;


					kabDigest = (String) imageMap.get("digest");
					image = (String) imageMap.get("imageName"); // docker.io/kabanero/nodejs
					if (image!=null) {
						StringTokenizer st = new StringTokenizer(image,"/");
						String containerRegistryURL = st.nextToken();
						String crNameSpace = st.nextToken();
						imageDigest = getImageDigestFromRegistry(name, versionNum, namespace, crNameSpace, containerRegistryURL);
					}
					
					versionMap.put("reponame", getRepoName(curatedStacks, name, versionNum));
					versionMap.put("digest check", digestCheck(kabDigest,imageDigest, stackStatusVersion.getStatus()));
					versionMap.put("kabanero digest", kabDigest);
					versionMap.put("image digest", imageDigest);
					status.add(versionMap);
				}
				allMap.put("name", name);
				Collections.sort(status, mapComparator2);
				allMap.put("status",status);
				allStacks.add(allMap);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return allStacks;
	}
	
	private static boolean isStatusPending(Stack stack, String version) {
		List<StackSpecVersions> versions = stack.getSpec().getVersions();
		for (StackSpecVersions specVersion : versions) {
			if (version.contentEquals(specVersion.getVersion())) {
				if ("active".contentEquals(specVersion.getDesiredState())) {
					return true;
				} else {
					break;
				}
			}
		}
		return false;
	}
	
	
	
	
	

	public static List filterNewStacks(List<Map> fromGit, StackList fromKabanero) {
		System.out.println("Entering filterNewStacks");
		ArrayList<Map> newStacks = new ArrayList<Map>();
		ArrayList<Map> registerVersionForName = new ArrayList<Map>();
		try {
			for (Map map : fromGit) {
				String name = (String) map.get("id");
				String version = (String) map.get("version");
				name = name.trim();
				version = version.trim();
				boolean match = false;
				HashMap gitMap = new HashMap();
				// check this git map against all of the kab stacks
				for (Stack kabStack : fromKabanero.getItems()) {
					String name1 = (String) kabStack.getSpec().getName();
					List<StackStatusVersions> versions = kabStack.getStatus().getVersions();
					name1 = name1.trim();
					// see if name from git matches name from kabanero
					// there can be multiple git maps that have the same name but different versions
					if (name1.contentEquals(name)) {
						System.out.println("name="+name+",git version="+version+",versions="+versions);
						// check if the version from the git map occurs in the list of versions
						// for this name matched stack map
						for (StackStatusVersions stackStatusVersions : versions) {
							if (version.contentEquals(stackStatusVersions.getVersion())) {
								match = true;
								HashMap versionForName = new HashMap();
								versionForName.put(name, version);
								registerVersionForName.add(versionForName);
								break;
							}
						}
					}
				}
				if (!match) {
					System.out.println("** NEW name="+map.get("id")+",git version="+version);
					gitMap.put("name", (String)map.get("id"));
					gitMap.put("version", version);
					gitMap.put("desiredState", "active");
					List<StackSpecImages> images=(List<StackSpecImages>)map.get("images");
					if (images == null) {
						String imageStr=(String) map.get("image");
						images = new ArrayList<StackSpecImages>();
						StackSpecImages stackSpecImages=new StackSpecImages();
						stackSpecImages.setImage(imageStr);
						images.add(stackSpecImages);
					}
					gitMap.put("images", images);
					gitMap.put("reponame", map.get("reponame"));
					newStacks.add(gitMap);
				}
			}
			System.out.println("newStacks: "+newStacks);
			System.out.println("registerVersionForName: "+registerVersionForName);
			// clean new stacks of any versions that were added extraneously
			for (Map newStack:newStacks) {
				boolean versionAlreadyThereFromGit = false;
				String name = (String) newStack.get("name");
				for (Map versionForName:registerVersionForName) {
					String version = (String) versionForName.get(name);
					String newStackVersion = (String) newStack.get("version");
					if (version!=null) {
						if (version.contentEquals(newStackVersion)) {
							versionAlreadyThereFromGit=true;
						}
					}
				}
				if (versionAlreadyThereFromGit) {
					System.out.println("removing: "+newStack);
					newStacks.remove(newStack);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newStacks;
	}
	
	
	public static List filterStacksToActivate(List<Map> fromGit, StackList fromKabanero) {
		ArrayList<Map> activateCollections = new ArrayList<Map>();
		try {
			for (Map map : fromGit) {
				String name = (String) map.get("id");
				String version = (String) map.get("version");
				name = name.trim();
				version = version.trim();
				boolean match = false;
				HashMap activateMap = new HashMap();
				for (Stack s : fromKabanero.getItems()) {
					String name1 = s.getMetadata().getName();
					name1 = name1.trim();
					StackStatus status = s.getStatus();
					List<StackStatusVersions> stackVersions=status.getVersions();
					for (StackStatusVersions stackVersion:stackVersions) {
						if (name1.contentEquals(name) && version.contentEquals(stackVersion.getVersion()) && "inactive".contentEquals(stackVersion.getStatus())) {
							activateMap.put("name", map.get("id"));
							activateMap.put("version", stackVersion.getVersion());
							activateMap.put("desiredState","active");
							List<StackSpecImages> images=(List<StackSpecImages>)map.get("images");
							if (images == null) {
								String imageStr=(String) map.get("image");
								images = new ArrayList<StackSpecImages>();
								StackSpecImages stackSpecImages = new StackSpecImages();
								stackSpecImages.setImage(imageStr);
								images.add(stackSpecImages);
							}
							activateMap.put("images", images);
							activateCollections.add(activateMap);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return activateCollections;
	}
	
	
	
	
	public static List<StackSpecVersions> getKabInstanceVersions(StackList fromKabanero, String name) {
		for (Stack s : fromKabanero.getItems()) {
			if (s.getSpec().getName().contentEquals(name)) {
				return s.getSpec().getVersions();
			}
		}
		return null;
	}
	
	public static Stack getKabInstance(StackList fromKabanero, String name) {
		for (Stack s : fromKabanero.getItems()) {
			if (s.getSpec().getName().contentEquals(name)) {
				return s;
			}
		}
		return null;
	}
	

	
	
	
	
	public static boolean isStackVersionInGit(List<Map> fromGit, String version, String name) {
		System.out.println("isStackVersionInGit");
		System.out.println("input parms - name: "+name+" version: "+version);
		try {
			for (Map map1 : fromGit) {
				String name1 = (String) map1.get("name");
				name1 = name1.trim();
				if (name1.contentEquals(name)) {
					List<Map> versions = (List<Map>) map1.get("versions");
					for (Map versionElement:versions) {
						String versionValue = (String) versionElement.get("version");
						if (version.equals(versionValue)) {
							return true;
						}
					}
				} 
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static ArrayList<Map> obsoleteStacks(StackList fromKabanero, List curatedStacks) {
		// iterate over collections to delete
		System.out.println("Starting DELETE processing");
		ArrayList<Map> deletedStacks = new ArrayList<Map>();
		try {

			for (Stack kabStack : fromKabanero.getItems()) {
				ArrayList<Map> versions = new ArrayList<Map>();
				HashMap m = new HashMap();
				

				List<StackSpecVersions> stackSpecVersions = new ArrayList<StackSpecVersions>();

				List<StackSpecVersions> kabStackVersions = kabStack.getSpec().getVersions();
				

				
				for (StackSpecVersions stackSpecVersion : kabStackVersions) {
					System.out.println("statusStackVersion: " + stackSpecVersion.getVersion() + " name: "
							+ kabStack.getSpec().getName());

					boolean isVersionInGitStack = StackUtils.isStackVersionInGit(curatedStacks,
							stackSpecVersion.getVersion(), kabStack.getSpec().getName());
					System.out.println("isVersionInGitStack: " + isVersionInGitStack);
					if (!isVersionInGitStack) {
						HashMap versionMap = new HashMap();
						versionMap.put("version", stackSpecVersion.getVersion());
						versions.add(versionMap);
					}
				}
				if (versions.size() > 0) {
					kabStack.getSpec().setVersions(stackSpecVersions);
					m.put("name", kabStack.getSpec().getName());

					m.put("versions", versions);

					deletedStacks.add(m);
				}
			}
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}
		return deletedStacks;
	}


	public static List<Map> packageStackMaps(List<Map> stacks) {
		ArrayList<Map> updatedStacks = new ArrayList<Map>();
		ArrayList versions = null;
		String saveName = "";
		for (Map stack : stacks) {
			System.out.println("packageStackMaps one stack: "+stack.toString());
			String name = (String) stack.get("id");
			if (name==null) {
				name = (String) stack.get("name");
			}
			// append versions and desiredStates to stack
			if (name.contentEquals(saveName)) {
				HashMap versionMap = new HashMap();
				versionMap.put("version", (String) stack.get("version"));
				
				JSONObject imageJson = new JSONObject();
				imageJson.put("image", (String) stack.get("image"));
				ArrayList images = new ArrayList();
				images.add(imageJson);
				
				versionMap.put("images", images);
				versionMap.put("reponame", stack.get("reponame"));
				versions.add(versionMap);
				Collections.sort(versions, mapComparator2);
			} 
			// creating stack object to add to new stacks List
			else {
				saveName = name;
				versions = new ArrayList<Map>();
				HashMap map = new HashMap();
				map.put("versions",versions);
				map.put("name",name);
				HashMap versionMap = new HashMap();
				versionMap.put("version", (String) stack.get("version"));
				
				JSONObject imageJson = new JSONObject();
				imageJson.put("image", (String) stack.get("image"));
				ArrayList images = new ArrayList();
				images.add(imageJson);
				
				versionMap.put("images", images);
				versionMap.put("reponame", stack.get("reponame"));
				versions.add(versionMap);
				updatedStacks.add(map);
			}
		}
		return updatedStacks;
	}
	
	
	
	public static List<Stack> packageStackObjects(List<Map> stacks, Map versionedStackMap) {
		ArrayList<Stack> updateStacks = new ArrayList<Stack>();
		ArrayList<StackSpecVersions> versions = null;
		StackSpec stackSpec = null;
		String saveName = "";
		System.out.println("versionedStackMap: "+versionedStackMap);
		for (Map stack : stacks) {
			System.out.println("packageStackObjects one stack: "+stack);
			String name = (String) stack.get("name");
			String version = (String) stack.get("version");
			System.out.println("packageStackObjects version="+version);
			// append versions and desiredStates to stack
			if (name.contentEquals(saveName)) {
				StackSpecVersions specVersion = new StackSpecVersions();
				specVersion.setDesiredState("active");
				specVersion.setVersion((String) stack.get("version"));
				specVersion.setImages((List<StackSpecImages>) stack.get("images"));
				specVersion.setPipelines((List<KabaneroSpecStacksPipelines>) versionedStackMap.get(name+"-"+version));
				versions.add(specVersion);
			} 
			// creating stack object to add to new stacks List
			else {
				saveName = name;
				versions = new ArrayList<StackSpecVersions>();
				stackSpec = new StackSpec();
				stackSpec.setVersions(versions);
				stackSpec.setName(name);
				Stack stackObj = new Stack();
				stackObj.setKind("Stack");
				stackObj.setSpec(stackSpec);
				StackSpecVersions specVersion = new StackSpecVersions();
				specVersion.setDesiredState("active");
				specVersion.setVersion(version);
				specVersion.setImages((List<StackSpecImages>) stack.get("images"));
				
				specVersion.setPipelines((List<KabaneroSpecStacksPipelines>) versionedStackMap.get(name+"-"+version));
				System.out.println("packageStackObjects one specVersion: "+specVersion);
				versions.add(specVersion);
				updateStacks.add(stackObj);
			}
		}
		return updateStacks;
	}

	public static boolean isStackVersionDeleted(List<Map> deletedStacks, String name, String version) {
		boolean match=false;
		for (Map deletedStack:deletedStacks) {
			String stackName = (String) deletedStack.get("name");
			stackName = stackName.trim();
			if (name.contentEquals(stackName)) {
				List<Map> versions=(List<Map>)deletedStack.get("versions");
				for (Map versionMap:versions) {
					String versionStr=(String)versionMap.get("version");
					versionStr = versionStr.trim();
					if (version.contentEquals(versionStr)) {
						System.out.println("name="+name+", version="+version+" has been deleted, so skip activate");
						match=true;
					}
				}
			}
		}
		return match;
	}


}
