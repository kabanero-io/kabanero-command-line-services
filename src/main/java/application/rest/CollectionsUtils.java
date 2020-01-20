package application.rest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

import com.google.gson.internal.LinkedTreeMap;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerStatus;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1PodStatus;

import io.kabanero.v1alpha1.client.apis.KabaneroApi;
import io.kabanero.v1alpha1.models.Collection;
import io.kabanero.v1alpha1.models.CollectionList;
import io.kabanero.v1alpha1.models.CollectionStatus;
import io.kabanero.v1alpha1.models.Kabanero;
import io.kabanero.v1alpha1.models.KabaneroList;
import io.kabanero.v1alpha1.models.KabaneroSpecCollections;
import io.kabanero.v1alpha1.models.KabaneroSpecCollectionsRepositories;

public class CollectionsUtils {
	
	public static boolean readGitSuccess=true;
	
	

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
	
	public static String getMasterCollectionUrl(Kabanero k) {
		try {
			List<KabaneroSpecCollectionsRepositories> collections = k.getSpec().getCollections().getRepositories();
			if ((collections != null) && (collections.size() > 0)) {
				return collections.get(0).getUrl();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	
	public static List getMasterCollectionWithREST(String user, String pw, String url) {
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
		System.out.println("response = " + response);
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
			HashMap outMap = new HashMap();
			outMap.put("name", name);
			outMap.put("version", version);
			aList.add(outMap);
		}
		return aList;
	}
	
	public static List filterActiveCollections(List<Map> fromKabanero) {
		ArrayList<Map> activeCollections = new ArrayList<Map>();

		try {
			for (Map map : fromKabanero) {
				HashMap activeMap = new HashMap();
				System.out.println("working on one collection: " + map);
				Map metadata = (Map) map.get("metadata");
				String name = (String) metadata.get("name");
				name = name.trim();
				Map spec = (Map) map.get("spec");
				String version = (String) spec.get("version");
				Map status = (Map) map.get("status");
				String statusStr = (String) status.get("status");
				if ("active".contentEquals(statusStr)) {
					activeMap.put("name", name);
					activeMap.put("version", version);
					activeMap.put("status","active");
					activeCollections.add(activeMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return activeCollections;
	}
	
	public static List allCollections(CollectionList fromKabanero) {
		ArrayList<Map> allCollections = new ArrayList<Map>();
		try {
			for (Collection c : fromKabanero.getItems()) {
				HashMap allMap = new HashMap();
				System.out.println("working on one collection: " + c);
				String name = c.getMetadata().getName();
				name = name.trim();
				String version = c.getSpec().getVersion();
				String statusStr = c.getStatus().getStatus();
				if (statusStr==null) {
					statusStr="initializing";
				}
				
				allMap.put("name", name);
				allMap.put("version", version);
				allMap.put("status",statusStr);
				System.out.println("all map: " + allMap);
				allCollections.add(allMap);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return allCollections;
	}
	
	public static List filterInActiveCollections(List<Map> fromKabanero) {
		ArrayList<Map> inActiveCollections = new ArrayList<Map>();

		try {
				for (Map map : fromKabanero) {
					HashMap inActiveMap = new HashMap();
					System.out.println("working on one collection: " + map);
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

	public static List filterNewCollections(List<Map> fromGit, CollectionList fromKabanero) {
		ArrayList<Map> newCollections = new ArrayList<Map>();

		try {
			for (Map map : fromGit) {
				String name = (String) map.get("id");
				String version = (String) map.get("version");
				name = name.trim();
				version = version.trim();
				boolean match = false;
				HashMap gitMap = new HashMap();
				for (Collection c : fromKabanero.getItems()) {
					String name1 = c.getMetadata().getName();
					name1 = name1.trim();
					if (name1.contentEquals(name)) {
						match = true;
					}
				}
				if (!match) {
					gitMap.put("name", map.get("id"));
					gitMap.put("version", version);
					gitMap.put("desiredState", "active");
					newCollections.add(gitMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newCollections;
	}
	
	
	public static List filterCollectionsToActivate(List<Map> fromGit, CollectionList fromKabanero) {
		ArrayList<Map> activateCollections = new ArrayList<Map>();

		try {
			for (Map map : fromGit) {
				String name = (String) map.get("id");
				String version = (String) map.get("version");
				name = name.trim();
				version = version.trim();
				boolean match = false;
				HashMap activateMap = new HashMap();
				for (Collection c : fromKabanero.getItems()) {
					String name1 = c.getMetadata().getName();
					name1 = name1.trim();
					CollectionStatus status = c.getStatus();
					String statusStr = null;
					if (status == null) {
						statusStr = "inactive";
					} else {
						statusStr = status.getStatus();
					}
					if (name1.contentEquals(name) && "inactive".contentEquals(statusStr)) {
						activateMap.put("name", map.get("id"));
						activateMap.put("version", version);
						activateMap.put("desiredState","active");
						activateCollections.add(activateMap);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return activateCollections;
	}

	public static List filterDeletedCollections(List<Map> fromGit, CollectionList fromKabanero) {
		ArrayList<Map> collectionsToDelete = new ArrayList<Map>();
		String name = null;
		String version = null;
		try {
			for (Collection c : fromKabanero.getItems()) {
				System.out.println("kab map: " + c);
				name = c.getMetadata().getName();
				version = c.getSpec().getVersion();
				name = name.trim();
				HashMap kabMap = new HashMap();
				boolean match = false;
				for (Map map1 : fromGit) {
					String name1 = (String) map1.get("id");
					name1 = name1.trim();
					if (name1.contentEquals(name)) {
						match = true;
					}
				}
				if (!match) {
					kabMap.put("name", name);
					kabMap.put("version", version);
					kabMap.put("desiredState", "inactive");
					collectionsToDelete.add(kabMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return collectionsToDelete;
	}

	public static List filterVersionChanges(List<Map> fromGit, CollectionList fromKabanero) {
		ArrayList<Map> versionChangeCollections = new ArrayList<Map>();
		try {
			for (Map map : fromGit) {
				String version = (String) map.get("version");
				String name = (String) map.get("id");
				name = name.trim();
				version = version.trim();
				boolean match = true;
				HashMap gitMap = new HashMap();
				String status = null;
				for (Collection c : fromKabanero.getItems()) {
					String name1 = c.getMetadata().getName();
					name1 = name1.trim();
					String version1 = c.getSpec().getVersion();
					CollectionStatus status1 = c.getStatus();
					version1 = version1.trim();
					if (name.contentEquals(name1)) {
						if (!version1.contentEquals(version)) {
							match = false;
							status = (String) c.getSpec().getDesiredState();
						}
					}
				}
				if (!match) {
					gitMap.put("name", map.get("id"));
					gitMap.put("version", version);
					gitMap.put("desiredState", status);
					versionChangeCollections.add(gitMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return versionChangeCollections;
	}

}
