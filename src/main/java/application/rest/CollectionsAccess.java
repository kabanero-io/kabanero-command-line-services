/*
 * Copyright (c) 2019 IBM Corporation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package application.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.security.RolesAllowed;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

//import io.kabanero.event.KubeUtils;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import kabasec.PATHelper;

@RolesAllowed("admin")
@Path("/v1")
public class CollectionsAccess {

	private static String version = "v1alpha1";

	private static Map envMap = System.getenv();
	
	private static String group = "kabanero.io";
	// should be array
	private static String namespace = (String) envMap.get("KABANERO_CLI_NAMESPACE");

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/version")
	public Response versionlist(@Context final HttpServletRequest request) {
		JSONObject msg = new JSONObject();
		msg.put("version", "0.2.0");
		return Response.ok(msg).build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response listCollections(@Context final HttpServletRequest request) {
		JSONObject msg = new JSONObject();
		try {

			String user = getUser(request);
			System.out.println("user=" + user);

			String PAT = getPAT();
			if (PAT==null) {
				System.out.println("login token has expired, please login again");
				JSONObject resp = new JSONObject();
				resp.put("message", "your login token has expired, please login again");
				return Response.status(401).entity(resp).build();
			}
			ArrayList<Map> masterCollections = (ArrayList<Map>) CollectionsUtils
					.getMasterCollectionWithREST(getUser(request), PAT, namespace);
			JSONArray ja = convertMapToJSON(CollectionsUtils.streamLineMasterMap(masterCollections));
			System.out.println("master collectionfor namespace: "+namespace+" kab group: " + group +"="+ ja);
			msg.put("master collections", ja);

			// make call to kabanero to get current collection

			ApiClient apiClient = KubeUtils.getApiClient();

			String plural = "collections";
			System.out.println("listing collections for namespace: "+namespace+" kab group: " + group);

			msg.put("active collections",
					convertMapToJSON(KubeUtils.listResources(apiClient, group, version, plural, namespace)));
			Map fromKabanero = null;
			try {
				fromKabanero = KubeUtils.mapResources(apiClient, group, version, plural, namespace);
			} catch (ApiException e) {
				e.printStackTrace();
			}

			List<Map> kabList = (List) fromKabanero.get("items");
			System.out.println(" ");
			System.out.println("List of active kab collections= "+kabList);
			System.out.println(" ");
			try {
				List<Map> newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections,
						kabList);
				List<Map> deleletedCollections = (List<Map>) CollectionsUtils
						.filterDeletedCollections(masterCollections, kabList);
				List<Map> versionChangeCollections = (List<Map>) CollectionsUtils
						.filterVersionChanges(masterCollections, kabList);

				ja = convertMapToJSON(newCollections);
				System.out.println("new collections= " + ja);
				msg.put("new collections", ja);

				ja = convertMapToJSON(deleletedCollections);
				System.out.println("obsolete collections= " + ja);
				msg.put("obsolete collections", ja);

				ja = convertMapToJSON(versionChangeCollections);
				System.out.println("version change collections= " + ja);
				msg.put("version change collections", ja);
			} catch (Exception e) {
				System.out.println("exception cause: " + e.getCause());
				System.out.println("exception message: " + e.getMessage());
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.ok(msg).build();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/onboard")
	public Response onboardDeveloper(@Context final HttpServletRequest request, final JSONObject jsonInput) {
		System.out.println("if only we could onboard you...");
		String gituser = (String) jsonInput.get("gituser");
		System.out.println("gituser: \"" + gituser + "\"");
		String repoName = (String) jsonInput.get("repoName");
		System.out.println("repoName: \"" + repoName + "\"");
		String workaround = "Command development in progress, please go to the tekton dashboard in your browser and manually configure the webhook";
		if (gituser != null) {
			workaround += " For gituser: " + gituser;
		}
		JSONObject msg = new JSONObject();
		msg.put("message", workaround);

		return Response.status(501).entity(msg).build();
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
			e.printStackTrace();
		}

		String plural = "collections";

		Map fromKabanero = null;
		try {
			fromKabanero = KubeUtils.mapResources(apiClient, group, version, plural, namespace);
		} catch (ApiException e) {
			e.printStackTrace();
		}

		List<Map> newCollections = null;
		List<Map> deleletedCollections = null;
		List<Map> versionChangeCollections = null;
		JSONObject msg = new JSONObject();
		try {
			
			List<Map> kabList = (List) fromKabanero.get("items");
			System.out.println(" ");
			System.out.println("List of active kab collections= "+kabList);
			
			String PAT = getPAT();
			if (PAT==null) {
				System.out.println("login token has expired, please login again");
				JSONObject resp = new JSONObject();
				resp.put("message", "your login token has expired, please login again");
				return Response.status(401).entity(resp).build();
			}
			
			List<Map> masterCollections = (ArrayList<Map>) CollectionsUtils
					.getMasterCollectionWithREST(getUser(request), PAT, namespace);
			System.out.println(" ");
			System.out.println("List of active master collections= "+masterCollections);
			
			System.out.println(" ");
			System.out.println(" ");
			newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections, kabList);
			System.out.println("*** new collections=" + newCollections);
			System.out.println(" ");

			deleletedCollections = (List<Map>) CollectionsUtils.filterDeletedCollections(masterCollections, kabList);
			System.out.println("*** collectionsto delete=" + deleletedCollections);
			System.out.println(" ");

			versionChangeCollections = (List<Map>) CollectionsUtils.filterVersionChanges(masterCollections, kabList);
			System.out.println("*** version Change Collections=" + versionChangeCollections);

		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}
		System.out.println("starting refresh");

		// iterate over new collections and activate
		try {
			for (Map m : newCollections) {
				try {
					JsonObject jo = makeJSONBody(m, namespace);
					System.out.println("json object for activate: " + jo);
					KubeUtils.createResource(apiClient, group, version, plural, namespace, jo);
					System.out.println("*** collection " + m.get("name") + " activated, organization "+group);
					m.put("status", m.get("name") + " activated");
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** collection " + m.get("name") + " failed to activate, organization "+group);
					e.printStackTrace();
					m.put("status", m.get("name") + " activation failed");
					m.put("exception", e.getMessage());
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
					System.out.println("json object for version change: " + jo);
					KubeUtils.updateResource(apiClient, group, version, plural, namespace, m.get("name").toString(),
							jo);
					System.out.println(
							"*** " + m.get("name") + "version change completed, new version number: " + version+", organization "+group);
					m.put("status", m.get("name") + "version change completed, new version number: " + version);
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** " + m.get("name") + "version change failed organization "+group);
					e.printStackTrace();
					m.put("status", m.get("name") + "version change failed");
					m.put("exception", e.getMessage());
				}
			}
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}

		// log successful changes too!
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
			JSONObject jo = new JSONObject();
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

		String plural = "collections";

		JSONObject msg = new JSONObject();

		try {
			int rc = KubeUtils.deleteKubeResource(apiClient, namespace, name, group, version, plural);
			if (rc == 0) {
				System.out.println("*** " + "Collection name: " + name + " deactivated");
				msg.put("status", "Collection name: " + name + " deactivated");
				return Response.ok(msg).build();
			}
			else if (rc == 404) {
				System.out.println("*** " + "Collection name: " + name + " 404 not found");
				msg.put("status", "Collection name: " + name + " 404 not found");
				return Response.status(400).entity(msg).build();
			} else {
				System.out.println("*** " + "Collection name: " + name + " was not deactivated, rc="+rc);
				msg.put("status", "Collection name: " + name + " was not deactivated, rc="+rc);
				return Response.status(400).entity(msg).build();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.put("status",
					"Collection name: " + name + " failed to deactivate, exception message: " + e.getMessage());
			return Response.status(400).entity(msg).build();
		}

	}

	private JsonObject makeJSONBody(Map m, String namespace) {

		System.out.println("makingJSONBody: " + m.toString());

		String joString = "{" + "    \"apiVersion\": \"kabanero.io/" + version + "\"," + "    \"kind\": \"Collection\","
				+ "    \"metadata\": {" + "        \"name\": \"{{__NAME__}}\","
				+ "        \"namespace\": \"{{__NAMESPACE__}}\"," + "        \"annotations\": {"
				+ "              \"collexion_id\": \"{{__COLLEXION_ID__}}\"" + "        }" + "    },"
				+ "    \"spec\": {" + "        \"version\": \"{{__VERSION__}}\"" + "    }" + "}";

		String jsonBody = joString.replace("{{__NAME__}}", m.get("name").toString())
				.replace("{{__NAMESPACE__}}", namespace).replace("{{__VERSION__}}", (String) m.get("version"))
				.replace("{{__COLLEXION_ID__}}", (String) m.get("originalName"));

		JsonParser parser = new JsonParser();
		JsonElement element = parser.parse(jsonBody);
		JsonObject json = element.getAsJsonObject();

		return json;
	}

	private String getUser(HttpServletRequest request) {
		String user = null;
		try {
			user = request.getUserPrincipal().getName();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return user;
	}

	private String getPAT() {
		String PAT = null;
		try {
			PAT = (new PATHelper()).extractGithubAccessTokenFromSubject();
		} catch (Exception e) {
			System.out.println("login token has expired, please login again");
			return null;
		}
		

		return PAT;
	}

}