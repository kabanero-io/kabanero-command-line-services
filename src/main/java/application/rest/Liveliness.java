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
	@Path("/liveliness")
	public Response liveliness() {
		boolean healthy = true;
		if (CollectionsUtils.readGitSuccess && HttpUtils.accessGitSuccess) {
			healthy = true;
		} else {
			healthy = false;
		}

		if (!healthy) {
			return Response.status(503).build();
		} else {
			return Response.ok().build();
		}

	}

}
