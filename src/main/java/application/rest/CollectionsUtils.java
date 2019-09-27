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

import javax.ws.rs.WebApplicationException;

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
import io.kubernetes.client.ApiException;

public class CollectionsUtils {

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

	private static String getFromGit(String url, String user, String pw) {

		HttpClientBuilder clientBuilder = HttpClients.custom();
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(user, pw));
		clientBuilder.setDefaultCredentialsProvider(credsProvider);
		HttpClient client = clientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		request.addHeader("accept", "application/yaml");

		// add request header

		HttpResponse response = null;
		;
		try {
			response = client.execute(request);
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Response Code : " + response.getStatusLine().getStatusCode());
		if (response.getStatusLine().getStatusCode()==429) {
			return "http code 429: GIT retry Limited Exceeded, please try again in 2 minutes";
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

	public static List getMasterCollectionWithREST(String user, String pw, String namespace) {
		String url = null;
		try {
			ApiClient apiClient = KubeUtils.getApiClient();
			String group = "kabanero.io";
			String version = "v1alpha1";
			String plural = "kabaneros";
			LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) KubeUtils.mapResources(apiClient, group, version, plural,
					namespace);
			List<Map> list = (List) map.get("items");
			boolean first = true;
			for (Map m : list) {
				if (!first)
					break;
				Map spec = (Map) m.get("spec");
				Map collections = (Map) spec.get("collections");
				System.out.println("collections=" + collections);
				List repos = (List) collections.get("repositories");
				System.out.println("repos=" + repos);
				Map repo = (Map) repos.get(0);
				url = (String) repo.get("url");
				first = false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

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
			String originalName = (String) map.get("name");
			String version = (String) map.get("version");
			HashMap outMap = new HashMap();
			outMap.put("name", name);
			outMap.put("originalName", originalName);
			outMap.put("version", version);
			aList.add(outMap);
		}
		return aList;
	}

	public static List filterNewCollections(List<Map> fromGit, List<Map> fromKabanero) {
		ArrayList<Map> newCollections = new ArrayList<Map>();

		try {
			for (Map map : fromGit) {
				String name = (String) map.get("id");
				String version = (String) map.get("version");
				name = name.trim();
				version = version.trim();
				boolean match = false;
				HashMap gitMap = new HashMap();
				for (Map map1 : fromKabanero) {
					Map metadata = (Map) map1.get("metadata");
					String name1 = (String) metadata.get("name");
					name1 = name1.trim();
					if (name1.contentEquals(name)) {
						match = true;
					}
				}
				if (!match) {
					gitMap.put("name", map.get("id"));
					gitMap.put("originalName", map.get("name"));
					gitMap.put("version", version);
					newCollections.add(gitMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newCollections;
	}

	public static List filterDeletedCollections(List<Map> fromGit, List<Map> fromKabanero) {
		ArrayList<Map> collectionsToDelete = new ArrayList<Map>();
		String name = null;
		String version = null;
		try {
			for (Map map : fromKabanero) {
				System.out.println("kab map: " + map);
				Map metadata = (Map) map.get("metadata");
				name = (String) metadata.get("name");
				Map spec = (Map) map.get("spec");
				version = (String) spec.get("version");
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
					collectionsToDelete.add(kabMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return collectionsToDelete;
	}

	public static List filterVersionChanges(List<Map> fromGit, List<Map> fromKabanero) {
		ArrayList<Map> newCollections = new ArrayList<Map>();
		try {
			for (Map map : fromGit) {
				String version = (String) map.get("version");
				String name = (String) map.get("id");
				name = name.trim();
				version = version.trim();
				boolean match = true;
				HashMap gitMap = new HashMap();
				for (Map map1 : fromKabanero) {
					Map metadata = (Map) map1.get("metadata");
					String name1 = (String) metadata.get("name");
					name1 = name1.trim();
					Map spec = (Map) map1.get("spec");
					String version1 = (String) spec.get("version");
					version1 = version1.trim();
					if (name.contentEquals(name1)) {
						if (!version1.contentEquals(version)) {
							match = false;
						}
					}
				}
				if (!match) {
					gitMap.put("name", map.get("id"));
					gitMap.put("originalName", map.get("name"));
					gitMap.put("version", version);
					newCollections.add(gitMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newCollections;
	}

}
