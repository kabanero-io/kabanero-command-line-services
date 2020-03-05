package application.rest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.yaml.snakeyaml.Yaml;

import io.kabanero.v1alpha2.client.apis.KabaneroApi;
import io.kabanero.v1alpha2.models.Stack;
import io.kabanero.v1alpha2.models.StackList;
import io.kabanero.v1alpha2.models.StackSpec;
import io.kabanero.v1alpha2.models.StackSpecImages;
import io.kabanero.v1alpha2.models.StackSpecVersions;
import io.kabanero.v1alpha2.models.StackStatus;
import io.kabanero.v1alpha2.models.StackStatusVersions;
import io.kabanero.v1alpha2.models.Kabanero;
import io.kabanero.v1alpha2.models.KabaneroList;
import io.kabanero.v1alpha2.models.KabaneroSpecStacks;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksGitRelease;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksHttps;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksPipelines;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksRepositories;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ContainerStatus;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.proto.Meta.Status;

public class StackUtils {
	
	// this value needs to start out as true, otherwise the liveness probe can't come up 
	// as healthy initially
	public static boolean readGitSuccess=true;
	
	public static Comparator<Map<String, String>> mapComparator = new Comparator<Map<String, String>>() {
	    public int compare(Map<String, String> m1, Map<String, String> m2) {
	        return m1.get("name").compareTo(m2.get("name"));
	    }
	};
	
	

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

	public static String getFromGit(String url, String user, String pw) {
		HttpClientBuilder clientBuilder = HttpClients.custom();
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(user, pw));
		clientBuilder.setDefaultCredentialsProvider(credsProvider);
		HttpClient client = clientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		request.addHeader("accept", "application/yaml");
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
	
	
	private static String getGithubFile(String repoOwner, String PAT, String URL, String REPONAME, String FILENAME) {
		// OAuth2 token authentication
		GitHubClient client = new GitHubClient(URL);
		client.setOAuth2Token(PAT);
		RepositoryService repoService = new RepositoryService(client);
		String fileContent = null, valueDecoded = null;
		try {
			Repository repo = repoService.getRepository(repoOwner, REPONAME);

			// now contents service
			ContentsService contentService = new ContentsService(client);
			List<RepositoryContents> test = contentService.getContents(repoService.getRepository(repoOwner, REPONAME),
					FILENAME);
			for (RepositoryContents content : test) {
				fileContent = content.getContent();
				valueDecoded = new String(Base64.decodeBase64(fileContent.getBytes()));
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return valueDecoded;
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
				String org, project, release, asset;
				url = kabaneroSpecStacksGitRelease.getHostname();
				System.out.println("GHE git url="+url);
				org = kabaneroSpecStacksGitRelease.getOrganization();
				project = kabaneroSpecStacksGitRelease.getProject();
				release = kabaneroSpecStacksGitRelease.getRelease();
				asset = kabaneroSpecStacksGitRelease.getAssetName();
				response = getGithubFile(org, KubeUtils.getSecret(namespace,url), url, project, asset);
				System.out.println("GHE response="+response);
			} else {
				response = getFromGit(url, user, pw);

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
	

	
	public static List allStacks(StackList fromKabanero) {
		ArrayList<Map> allStacks = new ArrayList<Map>();
		try {
			for (Stack s : fromKabanero.getItems()) {
				HashMap allMap = new HashMap();
				//System.out.println("working on one collection: " + s);
				String name = s.getMetadata().getName();
				name = name.trim();
				List<StackStatusVersions> versions = s.getStatus().getVersions();
				List<Map> status = new ArrayList<Map>();
				for (StackStatusVersions stackStatusVersion : versions) {
					HashMap versionMap = new HashMap();
					String statusStr = stackStatusVersion.getStatus();
					if ("inactive".contentEquals(statusStr)) {
						if (isStatusPending(s, stackStatusVersion.getVersion())) {
							statusStr = "active pending";
						}
					}
					versionMap.put("status", statusStr);
					versionMap.put("version", stackStatusVersion.getVersion());
					status.add(versionMap);
				}
				allMap.put("name", name);
				allMap.put("status",status);
				allStacks.add(allMap);
			}
		} catch (Exception e) {
			e.printStackTrace();
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
					System.out.println("versions: "+versions);
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
		ArrayList<Map> versions = null;
		String saveName = "";
		for (Map stack : stacks) {
			System.out.println("packageStackMaps one stack: "+stack.toString());
			String name = (String) stack.get("name");
			// append versions and desiredStates to stack
			if (name.contentEquals(saveName)) {
				HashMap versionMap = new HashMap();
				versionMap.put("version", (String) stack.get("version"));
				versionMap.put("images", stack.get("images"));
				versionMap.put("reponame", stack.get("reponame"));
				versions.add(versionMap);
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
				versionMap.put("images", stack.get("images"));
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
				specVersion.setPipelines((List<KabaneroSpecStacksPipelines>) versionedStackMap.get(name));
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
				
				specVersion.setPipelines((List<KabaneroSpecStacksPipelines>) versionedStackMap.get(name));
				System.out.println("packageStackObjects one specVersion: "+specVersion);
				versions.add(specVersion);
				updateStacks.add(stackObj);
			}
		}
		return updateStacks;
	}


}
