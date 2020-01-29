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

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.yaml.snakeyaml.Yaml;

import io.kabanero.v1alpha2.client.apis.KabaneroApi;
import io.kabanero.v1alpha2.models.Stack;
import io.kabanero.v1alpha2.models.StackList;
import io.kabanero.v1alpha2.models.StackSpec;
import io.kabanero.v1alpha2.models.StackSpecImages;
import io.kabanero.v1alpha2.models.StackSpecPipelines;
import io.kabanero.v1alpha2.models.StackSpecVersions;
import io.kabanero.v1alpha2.models.StackStatus;
import io.kabanero.v1alpha2.models.StackStatusVersions;
import io.kabanero.v1alpha2.models.Kabanero;
import io.kabanero.v1alpha2.models.KabaneroList;
import io.kabanero.v1alpha2.models.KabaneroSpecStacks;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksRepositories;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ContainerStatus;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.proto.Meta.Status;

public class StackUtils {
	
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
			return "http code 429: Github retry Limited Exceeded, please try again in 2 minutes";
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
	
	public static List<Map> getMasterStacks(Kabanero k) {
		
		ArrayList<Map> maps = new ArrayList<Map>();
		try {
			HashMap map = new HashMap();
			KabaneroSpecStacks kabaneroSpecStacks = k.getSpec().getStacks();
			kabaneroSpecStacks.getRepositories();
			kabaneroSpecStacks.getPipelines();  // take as is and set as list into stack CR being created
			//stack.getImages ??
//			for (??:??) {
//				
//			}
			return maps;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	
	public static List getStackFromGIT(String user, String pw, String url) {
		String response = null;
		try {
			response = getFromGit(url, user, pw);
			if (response!=null) {
				if (response.contains("http code 429:")) {
					ArrayList<String> list= new ArrayList();
					list.add(response);
					return list;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		//System.out.println("response = " + response);
		ArrayList<Map> list = null;
		try {
			Map m = readYaml(response);
			list = (ArrayList<Map>) m.get("stacks");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}
	
	
	

	public static List streamLineMasterMap(List<Map> list) {
		ArrayList aList = new ArrayList();
		for (Map map : list) {
			String name = (String) map.get("id");
			String version = (String) map.get("version");
			List<Map> images = (List<Map>) map.get("images");
			List<StackSpecImages> stackSpecImages = new ArrayList<StackSpecImages>();
			for (Map image: images) {
				StackSpecImages stackSpecImage = new StackSpecImages();
				stackSpecImage.setImage((String) image.get("image"));
				stackSpecImages.add(stackSpecImage);
			}
			Map imageMap=(Map)images.get(0);
			HashMap outMap = new HashMap();
			outMap.put("name", name);
			outMap.put("version", version);
			outMap.put("images", stackSpecImages);
			aList.add(outMap);
		}
		return aList;
	}
	
//	public static List filterActiveCollections(List<Map> fromKabanero) {
//		ArrayList<Map> activeCollections = new ArrayList<Map>();
//
//		try {
//			for (Map map : fromKabanero) {
//				HashMap activeMap = new HashMap();
//				System.out.println("working on one collection: " + map);
//				Map metadata = (Map) map.get("metadata");
//				String name = (String) metadata.get("name");
//				name = name.trim();
//				Map spec = (Map) map.get("spec");
//				String version = (String) spec.get("version");
//				Map status = (Map) map.get("status");
//				String statusStr = (String) status.get("status");
//				if ("active".contentEquals(statusStr)) {
//					activeMap.put("name", name);
//					activeMap.put("version", version);
//					activeMap.put("status","active");
//					activeCollections.add(activeMap);
//				}
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return activeCollections;
//	}
	
	public static List allStacks(StackList fromKabanero) {
		ArrayList<Map> allStacks = new ArrayList<Map>();
		try {
			for (Stack s : fromKabanero.getItems()) {
				HashMap allMap = new HashMap();
				//System.out.println("working on one collection: " + s);
				String name = s.getMetadata().getName();
				name = name.trim();
				List<StackStatusVersions> versions = s.getStatus().getVersions();
				HashMap versionMap = new HashMap();
				List<Map> status = new ArrayList<Map>();
				for (StackStatusVersions stackStatusVersion : versions) {
					versionMap.put("status", stackStatusVersion.getStatus());
					versionMap.put("version", stackStatusVersion.getVersion());
					status.add(versionMap);
				}
				allMap.put("name", name);
				allMap.put("status",status);
				//System.out.println("all map: " + allMap);
				allStacks.add(allMap);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return allStacks;
	}
	
	public static List filterInActiveCollections(List<Map> fromKabanero) {
		ArrayList<Map> inActiveCollections = new ArrayList<Map>();

		try {
				for (Map map : fromKabanero) {
					HashMap inActiveMap = new HashMap();
					//System.out.println("working on one collection: " + map);
					Map metadata = (Map) map.get("metadata");
					String name = (String) metadata.get("name");
					name = name.trim();
					Map spec = (Map) map.get("spec");
					String version = (String) spec.get("version");
					Map status = (Map) map.get("status");
					String statusStr = (String) status.get("status");
					if ("inactive".contentEquals(statusStr)) {
						inActiveMap.put("name", name);
						inActiveMap.put("version", version);
						inActiveMap.put("status","inactive");
						inActiveCollections.add(inActiveMap);
					}
				}
				
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return inActiveCollections;
	}
	

	public static List filterNewStacks(List<Map> fromGit, StackList fromKabanero) {
		ArrayList<Map> newStacks = new ArrayList<Map>();

		try {
			List<Map> kabMaps = multiVersionStacksMaps(fromKabanero);
			for (Map map : fromGit) {
				String name = (String) map.get("id");
				String version = (String) map.get("version");
				name = name.trim();
				version = version.trim();
				boolean match = false;
				HashMap gitMap = new HashMap();
				for (Map kab: kabMaps) {
					String name1 = (String) kab.get("name");
					String version1 = (String) kab.get("version");
					name1 = name1.trim();
					if (name1.contentEquals(name) && version1.contentEquals(version)) {
						match = true;
					}
				}
				if (!match) {
					gitMap.put("name", (String)map.get("id"));
					gitMap.put("version", version);
					gitMap.put("desiredState", "active");
					gitMap.put("images", map.get("images"));
					newStacks.add(gitMap);
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
	
	
	private static List<Map> multiVersionStacksMaps(StackList fromKabanero) {
		List<Map> kabMaps = new ArrayList<Map>();
		for (Stack s : fromKabanero.getItems()) {
			List<StackSpecVersions> versions = s.getSpec().getVersions();
			Map map=new HashMap();
			for (StackSpecVersions version: versions) {
				map.put("name",s.getMetadata().getName());
				map.put("version",version.getVersion());
				map.put("desiredState", version.getDesiredState());
			}
			kabMaps.add(map);
		}
		return kabMaps;
	}
	
//	public static List countSingleVersionDeletedStacks(List<Map> stacksToDelete) {
//		for(Map stack : stacksToDelete) {
//			String name = (String) stack.get("name");
//			int i=0;
//			for(Map stack2 : stacksToDelete) {
//				String name2 = (String) stack2.get("name");
//				if (name.contentEquals(name2)) {
//					i++;
//				}
//			}
//			stack.put("count", i);
//		}
//		return null;
//	}
	
	public static List isVerionInGitForStack(List<Map> fromGit, List<Map> stacksToDelete) {
		
		for(Map stack : stacksToDelete) {
			String name = (String) stack.get("name");
			int i=0;
			for(Map stack2 : stacksToDelete) {
				String name2 = (String) stack2.get("name");
				if (name.contentEquals(name2)) {
					i++;
				}
			}
			stack.put("count", i);
		}
		return null;
	}

	public static List filterDeletedStacks(List<Map> fromGit, StackList fromKabanero) {
		ArrayList<Map> stacksToDelete = new ArrayList<Map>();
		String name = null;
		String version = null;
		try {
			List<Map> kabMaps = multiVersionStacksMaps(fromKabanero);
			for (Map kab: kabMaps) {
				//System.out.println("kab map: " + kab);
				name = (String) kab.get("name");
				version = (String) kab.get("version");
				boolean match = false;
				Map kabMap = new HashMap();
				// is this Kabanero CR version in GIT hub?
				for (Map map1 : fromGit) {
					String name1 = (String) map1.get("id");
					name1 = name1.trim();
					String version1 = (String) map1.get("version");
					name1 = name1.trim();
					if (name1.contentEquals(name) && version1.contentEquals(version)) {
						match = true;
					}
				}
				// if this version is not in GIT hub add it to map element for deletion
				if (!match) {
					kabMap.put("name", name);
					kabMap.put("version", version);
					stacksToDelete.add(kabMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return stacksToDelete;
	}
	
	
	public static List filterVersionsToAdd(List<Map> fromGit, StackList fromKabanero) {
		ArrayList<Map> stacksToDelete = new ArrayList<Map>();
		String name = null;
		String version = null;
		try {
			List<Map> kabMaps = multiVersionStacksMaps(fromKabanero);
			for (Map kab: kabMaps) {
				//System.out.println("kab map: " + kab);
				name = (String) kab.get("name");
				version = (String) kab.get("version");
				boolean match = false;
				Map kabMap = new HashMap();
				for (Map map1 : fromGit) {
					String name1 = (String) map1.get("id");
					name1 = name1.trim();
					String version1 = (String) map1.get("version");
					name1 = name1.trim();
					if (name1.contentEquals(name) && version1.contentEquals(version)) {
						match = true;
					} 
				}
				if (!match) {
					kabMap.put("name", name);
					kabMap.put("version", version);
					stacksToDelete.add(kabMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return stacksToDelete;
	}
	
	public static List<Map> packageStackMaps(List<Map> stacks) {
		ArrayList<Map> newStacks = new ArrayList<Map>();
		ArrayList<String> versions = null;
		String saveName = "";
		for (Map stack : stacks) {
			System.out.println("one stack: "+stack.toString());
			String name = (String) stack.get("name");
			// append versions and desiredStates to stack
			if (name.contentEquals(saveName)) {
				versions.add((String) stack.get("version"));
			} 
			// creating stack object to add to new stacks List
			else {
				saveName = name;
				versions = new ArrayList<String>();
				HashMap map = new HashMap();
				map.put("versions",versions);
				map.put("name",name);
				map.put("images",stack.get("images"));
				versions.add((String) stack.get("version"));
				newStacks.add(map);
			}
		}
		return newStacks;
	}
	
	public static List<Stack> packageStackObjects(List<Map> stacks, Map versionedStackMap) {
		ArrayList<Stack> newStacks = new ArrayList<Stack>();
		ArrayList<StackSpecVersions> versions = null;
		StackSpec stackSpec = null;
		String saveName = "";
		for (Map stack : stacks) {
			String name = (String) stack.get("name");
			// append versions and desiredStates to stack
			if (name.contentEquals(saveName)) {
				StackSpecVersions specVersion = new StackSpecVersions();
				specVersion.setDesiredState("active");
				specVersion.setVersion((String) stack.get("version"));
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
				stackObj.setSpec(stackSpec);
				StackSpecVersions specVersion = new StackSpecVersions();
				specVersion.setDesiredState("active");
				specVersion.setVersion((String) stack.get("version"));
				specVersion.setImages((List<StackSpecImages>) stack.get("images"));
				
				specVersion.setPipelines((List<StackSpecPipelines>) versionedStackMap.get(name));
				
				versions.add(specVersion);
				newStacks.add(stackObj);
			}
		}
		return newStacks;
	}

	
	

//	public static List filterVersionChanges(List<Map> fromGit, StackList fromKabanero) {
//		ArrayList<Map> versionChangeCollections = new ArrayList<Map>();
//		List<Map> kabMaps = multiVersionStacksMaps(fromKabanero);
//		try {
//			for (Map map : fromGit) {
//				String version = (String) map.get("version");
//				String name = (String) map.get("id");
//				name = name.trim();
//				version = version.trim();
//				boolean match = true;
//				HashMap gitMap = new HashMap();
//				StackStatus status = null;
//				List<StackSpecVersions> versions=null;
//				for (Stack s : fromKabanero.getItems()) {
//					String name1 = s.getMetadata().getName();
//					name1 = name1.trim();
//					versions = s.getSpec().getVersions();
//					StackStatus status1 = s.getStatus();
//					version1 = version1.trim();
//					if (name.contentEquals(name1)) {
//						if (!version1.contentEquals(version)) {
//							match = false;
//							status =  s.getStatus();
//						}
//					}
//				}
//				if (!match) {
//					gitMap.put("name", map.get("id"));
//					gitMap.put("versions", versions);
//					gitMap.put("desiredState", status);
//					versionChangeCollections.add(gitMap);
//				}
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return versionChangeCollections;
//	}

}
