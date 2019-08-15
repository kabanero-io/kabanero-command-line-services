package application.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import kabasec.PATHelper;

@Path("/v1")
public class CollectionsAccess {

	// Only support v4 stream protocol as it was available since k8s 1.4
	private static final String MEDIA_TYPE = "application/vnd.kubernetes.protobuf";

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response collections(@Context final HttpServletRequest request) {
		// List collections=null;
		// access git hub and read index.yaml (as per Ian Partridge and format response)
		// POJO to translate yaml to List
		String url="api.github.com";
		String repo = "kabanero-command-line-services";
		//String gitResponse = getGithubFile("davco01a", url, repo, "README.md");
		String gitResponse = getGithubFile("davco01a", url, repo, "index.yaml");
		
		 
		JSONObject msg = new JSONObject();
		try {
			Map m = readYaml("index.yaml");
			ArrayList<Map> list = (ArrayList) m.get("stacks");
			String collNames = "";
			for (Map map : list) {
				String name = (String) map.get("name");
				System.out.println(name);
				collNames = collNames + name + ",";
			}
			collNames = collNames.substring(0, collNames.length() - 1);
			msg.put("collections", collNames);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// msg.put("collections", "collections");
		return Response.ok(msg).build();
	}

	public static void printMap(Map mp) {
		Iterator it = mp.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			System.out.println(pair.getKey() + " = " + pair.getValue());
			it.remove(); // avoids a ConcurrentModificationException
		}
	}
	
	private String getUser(HttpServletRequest request) {
		String user=null;
		try {
			user =request.getUserPrincipal().getName();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return user;
	}
	
	private String getPAT() {
		return (new PATHelper()).extractGithubAccessTokenFromSubject();
	}

//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	@Path("/collections/{colllectionid}")
//	public Response readCollection(@Context final HttpServletRequest request,
//			@PathParam("colllectionid") final String colllectionid) {
//		JSONObject collection = null;
//		// access git hub and read collection.yaml (as per Ian Partridge and format
//		// response
//		String gitResponse = accessGitHub("listCollection", colllectionid);
//		// POJO to format collection.yaml to List
//		JSONObject msg = new JSONObject();
//		msg.put("collection", "collection");
//		return Response.ok(msg).build();
//
//	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections/{colllectionid}/activate")
	public Response activateCollection(@Context final HttpServletRequest request,
			@PathParam("colllectionid") final String colllectionid) {
		// kube call to activate collection
		
		KubeRestClient krc = new KubeRestClient();
		String cmd="";
		try {
			krc.issueKubeCommand(cmd);
		} catch (ApiException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		ApiClient client = null;
		try {
			client = ClientBuilder.cluster().build();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// set the global default api-client to the in-cluster one from above
		Configuration.setDefaultApiClient(client);

		// the CoreV1Api loads default api-client from global configuration.
		CoreV1Api api = new CoreV1Api();

		// invokes the CoreV1Api client
		V1PodList list = null;
		try {
			list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (V1Pod item : list.getItems()) {
			System.out.println(item.getMetadata().getName());
		}

		String response = "collection activated";
		JSONObject msg = new JSONObject();
		msg.put("response", list.toString());
		return Response.ok(msg).build();

	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections/{colllectionid}/deactivate")
	public Response deActivateCollection(@Context final HttpServletRequest request,
			@PathParam("colllectionid") final String colllectionid) {
		// kube call to deactivate collection
//		ApiClient defaultClient = Configuration.getDefaultApiClient();
//
//		String response="";
//		AppsV1Api apiInstance = new AppsV1Api();
//		try {
//		    V1APIResourceList result = apiInstance.getAPIResources();
//		    response=result.toString();
//		    System.out.println(result);
//		} catch (ApiException e) {
//		    System.err.println("Exception when calling AppsV1Api#getAPIResources");
//		    e.printStackTrace();
//		}

		// create a new array of 2 strings
		String[] cmdArray = new String[1];

		// first argument is the program we want to open
		cmdArray[0] = "cmd kubectl get pods";

		// create a process and execute cmdArray and currect environment
		Process process = null;
		try {
			process = Runtime.getRuntime().exec(cmdArray, null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

		StringBuilder output = new StringBuilder();
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				output.append(line + "\n");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int exitVal = 0;
		try {
			exitVal = process.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (exitVal == 0) {
			System.out.println("Success!");
			System.out.println(output);
			System.exit(0);
		} else {
			System.out.println("Failed!");
			System.out.println(output);
			System.exit(12);
		}
		JSONObject msg = new JSONObject();
		msg.put("response", output.toString());
		return Response.ok(msg).build();

	}

	

	

	private String getGithubFile(String user, String URL, String REPONAME, String FILENAME) {
		//user="ralanlittle";
		//OAuth2 token authentication
		GitHubClient client = new GitHubClient(URL);
		//client.setOAuth2Token(getPAT());
		client.setOAuth2Token("93b592940c320cb9c8a82ba0e53e78bcd0a505b1");
		//client.setCredentials("davco01a", "yu897237u!w");
		// first use token service
	    RepositoryService repoService = new RepositoryService(client);
	    String fileContent = null, valueDecoded=null;
	    try {
//	    	System.out.println(repoService.getRepository(user, REPONAME));
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

	private Map readYaml(String file) {
		Yaml yaml = new Yaml();
		Map<String, Object> obj = null;
		try {
			InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("WEB-INF/" + file);
			obj = yaml.load(inputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return obj;
	}

}