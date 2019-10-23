package application.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

import application.rest.CollectionsUtils;
import kabasec.HttpUtils;

@Path("/v1")
public class Liveliness {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/liveliness")
	public Response liveliness() {
		boolean healthy = true;
		if (CollectionsUtils.readGitSuccess && HttpUtils.accessGitSuccess) {
			healthy = true;
		} else {
			healthy = false;
		}

		JSONObject msg = new JSONObject();

		JSONArray checks = new JSONArray();
		JSONObject jo = new JSONObject();
		JSONObject jo1 = new JSONObject();
		
		
		jo1.put("readGitSuccess",CollectionsUtils.readGitSuccess);
		jo1.put("accessGitSuccess",HttpUtils.accessGitSuccess);
		jo.put("GIT", jo1);
		
		checks.add(jo);
		
		msg.put("checks", checks);

		if (!healthy) {
			msg.put("status", "DOWN");
			return Response.status(503).entity(msg).build();
		} else {
			System.out.print(".");
			msg.put("status", "UP");
			return Response.ok(msg).build();
		}

	}

}
