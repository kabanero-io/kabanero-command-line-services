package application.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
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
		// access git hub and read index.yaml (as per Ian Partridge and format response
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
		JSONObject msg = new JSONObject();
		msg.put("collection","collection");
		return Response.ok(msg).build();

	}
}