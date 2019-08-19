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

import org.apache.commons.codec.binary.Base64;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.yaml.snakeyaml.Yaml;

import com.ibm.json.java.JSONObject;

//import io.kabanero.event.KubeUtils;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import kabasec.PATHelper;

@RolesAllowed("test-roles@kabanero-io")
@Path("/v1")
public class CollectionsAccess {

	private boolean skip=true;
	private ArrayList<Map> getMasterCollectionsList() {
		String url="api.github.com";
		String repo = "kabanero-command-line-services";
		String repoOwnerID="kabanero-io";
		String gitResponse = getGithubFile(repoOwnerID, url, repo, "index.yaml");
		 
		
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
	@Path("/collections")
	public Response listCollections(@Context final HttpServletRequest request) {
		JSONObject msg=new JSONObject();
		try {
			ArrayList<Map> masterCollections = (ArrayList<Map>) getMasterCollectionsList();
			String collections = CollectionsUtils.changeCollectionIntoStringList(masterCollections);
			msg.put("master collection", collections);
						
			// make call to kabanero to get current collection
			ApiClient apiClient = KubeUtils.getApiClient();
			String group="kabanero.io";
			String version="v1alpha1";
			String plural="collections";
			String namespace="kabanero";
			msg.put("kabanero collection", KubeUtils.listResources(apiClient, group, version, plural, namespace));
			Map fromKabanero = null;	
			try {
				fromKabanero = KubeUtils.mapResources(apiClient, group, version, plural, namespace);
			} catch (ApiException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			List<Map> kabList=(List)fromKabanero.get("items");
			try {
				List<Map> newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections, kabList);
				List<Map> deleletedCollections = (List<Map>) CollectionsUtils.filterDeletedCollections(masterCollections, kabList);
				List<Map> versionChangeCollections = (List<Map>) CollectionsUtils.filterVersionChanges(masterCollections, kabList);
				msg.put("new collections", newCollections.toString());
				msg.put("obsolete collections", deleletedCollections.toString());
				msg.put("version change collections", versionChangeCollections.toString());
			} catch (Exception e) {
				System.out.println("exception cause: "+e.getCause());
				System.out.println("exception message: "+e.getMessage());
				e.printStackTrace();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.ok(msg).build();
	}
	
	
//	KubeRestClient krc = new KubeRestClient();
//	String cmd="";
//	try {
//		krc.post(cmd);
//	} catch (ApiException | IOException e1) {
//		// TODO Auto-generated catch block
//		e1.printStackTrace();
//	}
//
//	ApiClient client = null;
//	try {
//		client = ClientBuilder.cluster().build();
//	} catch (IOException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//
//	// set the global default api-client to the in-cluster one from above
//	Configuration.setDefaultApiClient(client);
//
//	// the CoreV1Api loads default api-client from global configuration.
//	CoreV1Api api = new CoreV1Api();
//
//	// invokes the CoreV1Api client
//	V1PodList list = null;
//	try {
//		list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
//	} catch (ApiException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//	for (V1Pod item : list.getItems()) {
//		System.out.println(item.getMetadata().getName());
//	}
//
//	String response = "collection activated";
//	JSONObject msg = new JSONObject();
//	msg.put("response", list.toString());
//	return Response.ok(msg).build();

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response refreshCollections(@Context final HttpServletRequest request) {
		// kube call to refresh collection
		ApiClient apiClient=null;
		try {
			apiClient = KubeUtils.getApiClient();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String group="kabanero.io";
		String version="v1alpha1";
		String plural="collections";
		String namespace="kabanero";
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
			List<Map> kabList=(List)fromKabanero.get("items");
			System.out.println("<2>");
			List<Map> masterCollections=getMasterCollectionsList();
			System.out.println("<3>");
			newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections, kabList);
			System.out.println("*** new collections="+newCollections);
			System.out.println(" ");
			deleletedCollections = (List<Map>) CollectionsUtils.filterDeletedCollections(masterCollections, kabList);
			System.out.println("*** deleted collections="+deleletedCollections);
			System.out.println(" ");
			versionChangeCollections = (List<Map>) CollectionsUtils.filterVersionChanges(masterCollections, kabList);
			System.out.println("*** version Change Collections="+versionChangeCollections);
			
			msg.put("new collections", newCollections.toString());
			msg.put("collections to delete", deleletedCollections.toString());
			msg.put("version change collections", versionChangeCollections.toString());
		} catch (Exception e) {
			System.out.println("exception cause: "+e.getCause());
			System.out.println("exception message: "+e.getMessage());
			e.printStackTrace();
		}
		if (!skip) {
			// iterate over new collections and activate
			try {
				for (Map m:newCollections) {
					try {
						KubeUtils.createResource(apiClient, group, version, plural, namespace, makeJSONBody(m,namespace));
						m.put("status", "activated");
					} catch (Exception e) {
						System.out.println("exception cause: "+e.getCause());
						System.out.println("exception message: "+e.getMessage());
						e.printStackTrace();
						m.put("status", "activation failed, exception message="+e.getMessage());
					}
				}
			} catch (Exception e) {
				System.out.println("exception cause: "+e.getCause());
				System.out.println("exception message: "+e.getMessage());
				e.printStackTrace();
			}

			// iterate over version change collections and update
			try {
				for (Map m : versionChangeCollections) {
					try {
						KubeUtils.setResourceStatus(apiClient,group, version, plural, namespace, m.get("name").toString(), makeJSONBody(m,namespace));
						m.put("status", "version change completed");
					} catch (Exception e) {
						System.out.println("exception cause: "+e.getCause());
						System.out.println("exception message: "+e.getMessage());
						e.printStackTrace();
						m.put("status", "version change failed, exception message="+e.getMessage());
					}
				}
			} catch (Exception e) {
				System.out.println("exception cause: " + e.getCause());
				System.out.println("exception message: " + e.getMessage());
				e.printStackTrace();
			}
		}
		return Response.ok(msg).build();
	}
	
	
	
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections/{name}")
	public Response deActivateCollection(@Context final HttpServletRequest request,
			@PathParam("name") final String name) throws Exception {
		// make call to kabanero to delete collection
		ApiClient apiClient = KubeUtils.getApiClient();
		String group="kabanero.io";
		String version="v1alpha1";
		String plural="collections";
		String namespace="kabanero";
		JSONObject msg = new JSONObject();
		if (!skip) {
			try {
				KubeUtils.deleteKubeResource(apiClient, namespace, name, group, version, plural);
				msg.put("status", "Collection name: "+name+" deactivated");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				msg.put("status", "Collection name: "+name+" failed to deactivate, exception message: "+e.getMessage());
			}
		}
		return Response.ok(msg).build();
	}
	
	private JSONObject makeJSONBody(Map m, String namespace) {
		JSONObject jo = new JSONObject();
		jo.put("apiVersion", "kabanero.io/v1alpha1");
		jo.put("kind", "Collection");
		JSONObject metadata = new JSONObject();
		metadata.put("name", m.get("name").toString());
		metadata.put("namespace", namespace);
		jo.put("metadata", metadata);
		JSONObject spec = new JSONObject();
		spec.put("version", m.get("version").toString());
		jo.put("spec", spec);
		return jo;
	}

	

	

	private String getGithubFile(String user, String URL, String REPONAME, String FILENAME) {
		//OAuth2 token authentication
		GitHubClient client = new GitHubClient(URL);
//		String PAT=getPAT();
//		System.out.println("PAT="+PAT);
//		client.setOAuth2Token(getPAT());
		//client.setOAuth2Token("xxxxxxxxx");
		//client.setCredentials("xxxxxx", "xxxxxxxxxxxx");
		
	    RepositoryService repoService = new RepositoryService(client);
	    String fileContent = null, valueDecoded=null;
	    try {
	        Repository repo = repoService.getRepository(user, REPONAME);

	        // now contents service
	        ContentsService contentService = new ContentsService(client);
	        List<RepositoryContents> test = contentService.getContents(repoService.getRepository(user, REPONAME), FILENAME);
	        for(RepositoryContents content : test){
	            fileContent = content.getContent();
	            valueDecoded= new String(Base64.decodeBase64(fileContent.getBytes() ));
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
			//InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("WEB-INF/" + file);
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
		String PAT=null;
		try {
			PAT=(new PATHelper()).extractGithubAccessTokenFromSubject();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return PAT;
	}

}