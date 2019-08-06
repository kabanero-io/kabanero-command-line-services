package application.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ApplicationPath;
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

import com.ibm.json.java.JSONObject;


@Path("/v1")
public class CollectionsAccess {
	@GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/collections")
    public Response collections(@Context final HttpServletRequest request) {
		//List collections=null;
		// access git hub and read index.yaml (as per Ian Partridge and format response)
		// POJO to translate yaml to List
		String gitResponse = accessGitHub("listCollections",null);
		JSONObject msg = new JSONObject();
		msg.put("collections","collections");
		return Response.ok(msg).build();
	}
	
	@GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/collections/{colllectionid}")
    public Response readCollection(@Context final HttpServletRequest request,
                             @PathParam("colllectionid") final String colllectionid) {
		JSONObject collection=null;
		// access git hub and read collection.yaml (as per Ian Partridge and format response
		String gitResponse = accessGitHub("listCollection",colllectionid);
		// POJO to format collection.yaml to List
		JSONObject msg = new JSONObject();
		msg.put("collection","collection");
		return Response.ok(msg).build();

	}
	
	@PUT
    @Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
    @Path("/collections/{colllectionid}/activate")
    public Response activateCollection(@Context final HttpServletRequest request,
                             @PathParam("colllectionid") final String colllectionid) {
		// kube call to activate collection
		String response="";
		JSONObject msg = new JSONObject();
		msg.put("response",response);
		return Response.ok(msg).build();

	}
	
	@PUT
    @Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
    @Path("/collections/{colllectionid}/deactivate")
    public Response deActivateCollection(@Context final HttpServletRequest request,
                             @PathParam("colllectionid") final String colllectionid) {
		// kube call to deactivate collection
		String response="";
		JSONObject msg = new JSONObject();
		msg.put("response",response);
		return Response.ok(msg).build();

	}
	
	@POST
    @Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
    @Path("/collections/{colllectionid}")
    public Response addCollection(@Context final HttpServletRequest request,
                             @PathParam("colllectionid") final String colllectionid) {
		// kube call to add collection
		String response="";
		JSONObject msg = new JSONObject();
		msg.put("response",response);
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
		String response="";
		JSONObject msg = new JSONObject();
		msg.put("response",response);
		return Response.ok(msg).build();

	}
	
	private String issueKubeCommand(String cmd) {
		String kubeResponse="";
		return kubeResponse;
	}
	
	private String accessGitHub(String func, String identifier) {
		// waiting on login/logout PAT code from Chun Long's team
		String gitResponse="";
		return gitResponse;
	}
}