package application.rest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.yaml.snakeyaml.Yaml;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

//import io.kabanero.event.KubeUtils;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import kabasec.PATHelper;

@RolesAllowed("test-roles@kabanero-io")
@Path("/v1")
public class CollectionsAccess {

	private boolean skip = false;

	private ArrayList<Map> getMasterCollectionsList() {
		String url = "api.github.com";
		String repo = "kabanero-command-line-services";
		String repoOwnerID = "kabanero-io";
		String gitResponse = getGithubFile(repoOwnerID, url, repo, "kabanero.yaml");

		ArrayList<Map> list = null;
		try {
			Map m = readYaml(gitResponse);
			list = (ArrayList<Map>) m.get("stacks");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/version")
	public Response versionlist(@Context final HttpServletRequest request) {
		JSONObject msg = new JSONObject();
		msg.put("version", "0.9");
		return Response.ok(msg).build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response listCollections(@Context final HttpServletRequest request) {
		JSONObject msg = new JSONObject();
		try {
			ArrayList<Map> masterCollections = (ArrayList<Map>) getMasterCollectionsList();
			//String collections = CollectionsUtils.changeCollectionIntoStringList(masterCollections);
			msg.put("master collection", convertMapToJSON(CollectionsUtils.streamLineMasterMap(masterCollections)));

			// make call to kabanero to get current collection
			if (!skip) {
				ApiClient apiClient = KubeUtils.getApiClient();
				String group = "kabanero.io";
				String version = "v1alpha1";
				String plural = "collections";
				String namespace = "kabanero";
				msg.put("kabanero collection", convertMapToJSON(KubeUtils.listResources(apiClient, group, version, plural, namespace)));
				Map fromKabanero = null;
				try {
					fromKabanero = KubeUtils.mapResources(apiClient, group, version, plural, namespace);
				} catch (ApiException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				List<Map> kabList = (List) fromKabanero.get("items");
				try {
					List<Map> newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections,
							kabList);
					List<Map> deleletedCollections = (List<Map>) CollectionsUtils
							.filterDeletedCollections(masterCollections, kabList);
					List<Map> versionChangeCollections = (List<Map>) CollectionsUtils
							.filterVersionChanges(masterCollections, kabList);
					msg.put("new collections", convertMapToJSON(newCollections));
					msg.put("obsolete collections", convertMapToJSON(deleletedCollections));
					msg.put("version change collections", convertMapToJSON(versionChangeCollections));
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					e.printStackTrace();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.ok(msg).build();
	}



	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response refreshCollections(@Context final HttpServletRequest request) {
		// kube call to refresh collection
		ApiClient apiClient = null;
		try {
			apiClient = KubeUtils.getApiClient();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String group = "kabanero.io";
		String version = "v1alpha1";
		String plural = "collections";
		String namespace = "kabanero";
		Map fromKabanero = null;
		try {
			fromKabanero = KubeUtils.mapResources(apiClient, group, version, plural, namespace);
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<Map> newCollections = null;
		List<Map> deleletedCollections = null;
		List<Map> versionChangeCollections = null;
		JSONObject msg = new JSONObject();
		try {
			System.out.println("<1>");
			List<Map> kabList = (List) fromKabanero.get("items");
			System.out.println("<2>");
			List<Map> masterCollections = getMasterCollectionsList();
			System.out.println("<3>");
			newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections, kabList);
			System.out.println("*** new collections=" + newCollections);
			System.out.println(" ");
			deleletedCollections = (List<Map>) CollectionsUtils.filterDeletedCollections(masterCollections, kabList);
			System.out.println("*** deleted collections=" + deleletedCollections);
			System.out.println(" ");
			versionChangeCollections = (List<Map>) CollectionsUtils.filterVersionChanges(masterCollections, kabList);
			System.out.println("*** version Change Collections=" + versionChangeCollections);

			
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}
		System.out.println("starting refresh");
		if (!skip) {
			// iterate over new collections and activate
			try {
				for (Map m : newCollections) {
					try {
						JsonObject jo = makeJSONBody(m, namespace);
						System.out.println("json object for activate: "+jo);
						KubeUtils.createResource(apiClient, group, version, plural, namespace,
								jo);
						m.put("status", m.get("name") + " activated");
					} catch (Exception e) {
						System.out.println("exception cause: " + e.getCause());
						System.out.println("exception message: " + e.getMessage());
						e.printStackTrace();
						m.put("status",  m.get("name") + " activation failed");
						m.put("exception",e.getMessage());
					}
				}
			} catch (Exception e) {
				System.out.println("exception cause: " + e.getCause());
				System.out.println("exception message: " + e.getMessage());
				e.printStackTrace();
			}

			// iterate over version change collections and update
			try {
				for (Map m : versionChangeCollections) {
					try {
						JsonObject jo = makeJSONBody(m, namespace);
						System.out.println("json object for version change: "+jo); 
						KubeUtils.updateResource(apiClient, group, version, plural, namespace,
								m.get("name").toString(), jo);
						m.put("status",  m.get("name") + "version change completed");
					} catch (Exception e) {
						System.out.println("exception cause: " + e.getCause());
						System.out.println("exception message: " + e.getMessage());
						e.printStackTrace();
						m.put("status",  m.get("name") + "version change failed");
						m.put("exception",e.getMessage());
					}
				}
			} catch (Exception e) {
				System.out.println("exception cause: " + e.getCause());
				System.out.println("exception message: " + e.getMessage());
				e.printStackTrace();
			}
		}
		try {
			msg.put("new collections", convertMapToJSON(newCollections));
			msg.put("collections to delete", convertMapToJSON(deleletedCollections));
			msg.put("version change collections", convertMapToJSON(versionChangeCollections));
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}
		System.out.println("finishing refresh");
		return Response.ok(msg).build();
	}
	
	private JSONArray convertMapToJSON(List<Map> list) {
		JSONArray ja = new JSONArray();
		for (Map m : list) {
			JSONObject jo=new JSONObject();
			jo.putAll(m);
			ja.add(jo);
		}
		return ja;
	}

	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections/{name}")
	public Response deActivateCollection(@Context final HttpServletRequest request,
			@PathParam("name") final String name) throws Exception {
		// make call to kabanero to delete collection
		ApiClient apiClient = KubeUtils.getApiClient();
		String group = "kabanero.io";
		String version = "v1alpha1";
		String plural = "collections";
		String namespace = "kabanero";
		JSONObject msg = new JSONObject();
		if (!skip) {
			try {
				KubeUtils.deleteKubeResource(apiClient, namespace, name, group, version, plural);
				msg.put("status", "Collection name: " + name + " deactivated");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				msg.put("status",
						"Collection name: " + name + " failed to deactivate, exception message: " + e.getMessage());
			}
		}
		return Response.ok(msg).build();
	}

	private JsonObject makeJSONBody(Map m, String namespace) {
		
		System.out.println("makingJSONBody: "+m.toString());
		
		String joString =
			    "{"+
			    "    \"apiVersion\": \"kabanero.io/v1alpha1\","+
			    "    \"kind\": \"Collection\","+
			    "    \"metadata\": {"+
			    "        \"name\": \"{{__NAME__}}\","+
			    "        \"namespace\": \"{{__NAMESPACE__}}\","+
			    "        \"annotations\": {"+
			    "              \"collexion_id\": \"{{__COLLEXION_ID__}}\""+
			    "        }"+
			    "    },"+
			    "    \"spec\": {"+
			    "        \"version\": \"{{__VERSION__}}\"" +
			    "    }"+
			    "}";
		
		String jsonBody = joString.replace("{{__NAME__}}", m.get("name").toString()).
                replace("{{__NAMESPACE__}}", namespace).
                replace("{{__VERSION__}}", (String) m.get("version")).
				replace("{{__COLLEXION_ID__}}", (String) m.get("originalName"));
		
		JsonParser parser= new JsonParser();
	       JsonElement element= parser.parse(jsonBody);
	       JsonObject json= element.getAsJsonObject();
		
		
		
		return json;
	}

	private String getGithubFile(String user, String URL, String REPONAME, String FILENAME) {
		// OAuth2 token authentication
		GitHubClient client = new GitHubClient(URL);
		String PAT = getPAT();
		System.out.println("PAT=" + PAT);
		client.setOAuth2Token(getPAT());
		// client.setOAuth2Token("xxxxxxxxx");
		// client.setCredentials("xxxxxx", "xxxxxxxxxxxx");

		RepositoryService repoService = new RepositoryService(client);
		String fileContent = null, valueDecoded = null;
		try {
			Repository repo = repoService.getRepository(user, REPONAME);

			// now contents service
			ContentsService contentService = new ContentsService(client);
			List<RepositoryContents> test = contentService.getContents(repoService.getRepository(user, REPONAME),
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

	private Map readYaml(String response) {
		Yaml yaml = new Yaml();
		Map<String, Object> obj = null;
		try {
			InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
			// InputStream inputStream =
			// this.getClass().getClassLoader().getResourceAsStream("WEB-INF/" + file);
			obj = yaml.load(inputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return obj;
	}

	private static void printMap(Map mp) {
		Iterator it = mp.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			System.out.println(pair.getKey() + " = " + pair.getValue());
			it.remove(); // avoids a ConcurrentModificationException
		}
	}

//	private String getUser(HttpServletRequest request) {
//		String user=null;
//		try {
//			user =request.getUserPrincipal().getName();
//		}
//		catch (Exception e) {
//			e.printStackTrace();
//		}
//		return user;
//	}

	private String getPAT() {
		String PAT = null;
		try {
			PAT = (new PATHelper()).extractGithubAccessTokenFromSubject();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return PAT;
	}

}