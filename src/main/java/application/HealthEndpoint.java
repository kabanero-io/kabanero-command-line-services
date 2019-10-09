package application;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

import application.rest.CollectionsUtils;
import kabasec.HttpUtils;

@Path("health")
public class HealthEndpoint {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response healthcheck() {
		boolean healthy = true;
		if (CollectionsUtils.readGitSuccess && HttpUtils.accessGitSuccess) {
			healthy = true;
		} else {
			healthy = false;
		}

		JSONObject msg = new JSONObject();

		JSONArray checks = new JSONArray();
		checks.add("GIT");
		msg.put("checks", checks);

		if (!healthy) {
			msg.put("status", "DOWN");
			return Response.status(503).entity(msg).build();
		} else {
			msg.put("status", "UP");
			return Response.ok(msg).build();
		}

	}

}
