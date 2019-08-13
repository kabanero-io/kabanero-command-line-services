package application.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.yaml.snakeyaml.Yaml;

import com.ibm.json.java.JSONObject;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;

@Path("/v1")
public class CollectionsAccess {

	// Only support v4 stream protocol as it was available since k8s 1.4
	public static final String V4_STREAM_PROTOCOL = "v4.channel.k8s.io";
	public static final String STREAM_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
	public static final String SPDY_3_1 = "SPDY/3.1";

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response collections(@Context final HttpServletRequest request) {
		// List collections=null;
		// access git hub and read index.yaml (as per Ian Partridge and format response)
		// POJO to translate yaml to List
		String gitResponse = accessGitHub("listCollections", null);
		JSONObject msg = new JSONObject();
		try {
			Map m = readYaml("index.yaml");
			ArrayList<Map> list = (ArrayList) m.get("stacks");
			String collNames="";
			for (Map map :list) {
				String name=(String) map.get("name");
				System.out.println(name);
				collNames=collNames+name+",";
			}
			collNames=collNames.substring(0, collNames.length() - 1);
			msg.put("collections", collNames);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		//msg.put("collections", "collections");
		return Response.ok(msg).build();
	}
	
	public static void printMap(Map mp) {
	    Iterator it = mp.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pair = (Map.Entry)it.next();
	        System.out.println(pair.getKey() + " = " + pair.getValue());
	        it.remove(); // avoids a ConcurrentModificationException
	    }
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections/{colllectionid}")
	public Response readCollection(@Context final HttpServletRequest request,
			@PathParam("colllectionid") final String colllectionid) {
		JSONObject collection = null;
		// access git hub and read collection.yaml (as per Ian Partridge and format
		// response
		String gitResponse = accessGitHub("listCollection", colllectionid);
		// POJO to format collection.yaml to List
		JSONObject msg = new JSONObject();
		msg.put("collection", "collection");
		return Response.ok(msg).build();

	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections/{colllectionid}/activate")
	public Response activateCollection(@Context final HttpServletRequest request,
			@PathParam("colllectionid") final String colllectionid) {
		// kube call to activate collection

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

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections/{colllectionid}")
	public Response addCollection(@Context final HttpServletRequest request,
			@PathParam("colllectionid") final String colllectionid) {
		// kube call to add collection
		String response = "";
		JSONObject msg = new JSONObject();
		msg.put("response", response);
		return Response.ok(msg).build();

	}

	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections/{colllectionid}")
	public Response removeCollection(@Context final HttpServletRequest request,
			@PathParam("colllectionid") final String colllectionid) {
		// need to develop and test in OKD env
		// kube call to delete collection
		String response = "";
		JSONObject msg = new JSONObject();
		msg.put("response", response);
		return Response.ok(msg).build();

	}

	private String issueKubeCommand(String cmd) {
		// Develop and test in OKD envs
		String kubeResponse = "";
		return kubeResponse;
	}

	private String accessGitHub(String func, String identifier) {
		// waiting on login/logout PAT code from Chun Long's team
		String gitResponse = "";
		return gitResponse;
	}

	private Map readYaml(String file) {
		Yaml yaml = new Yaml();
		Map<String, Object> obj = null;
		try {
			InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("WEB-INF/"+file);
			obj = yaml.load(inputStream);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return obj;
	}
	
	
}