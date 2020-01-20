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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

//import io.kabanero.event.KubeUtils;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import kabasec.PATHelper;

import io.kabanero.v1alpha1.client.apis.CollectionApi;
import io.kabanero.v1alpha1.client.apis.KabaneroApi;
import io.kabanero.v1alpha1.models.Collection;
import io.kabanero.v1alpha1.models.CollectionList;
import io.kabanero.v1alpha1.models.CollectionSpec;
import io.kabanero.v1alpha1.models.Kabanero;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;

@RolesAllowed("admin")
@Path("/v1")
public class CollectionsAccess {

	private static String version = "v1alpha1";

	private static Map envMap = System.getenv();
	
	private static String group = "kabanero.io";
	// should be array
	private static String namespace = (String) envMap.get("KABANERO_CLI_NAMESPACE");
																	
	public static Comparator<Map<String, String>> mapComparator = new Comparator<Map<String, String>>() {
	    public int compare(Map<String, String> m1, Map<String, String> m2) {
	        return m1.get("name").compareTo(m2.get("name"));
	    }
	};
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/image")
	public Response versionlist(@Context final HttpServletRequest request) {
		String image = null;
		try {
			image=CollectionsUtils.getImage(namespace);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		JSONObject msg = new JSONObject();
		msg.put("image", image);
		return Response.ok(msg).build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response listCollections(@Context final HttpServletRequest request) {
		JSONObject msg = new JSONObject();
		try {
			Kabanero k = CollectionsUtils.getKabaneroForNamespace(namespace);
			String url = CollectionsUtils.getMasterCollectionUrl(k);
			
			
			System.out.println("entering LIST function");
			String user = getUser(request);
			System.out.println("user=" + user);

			String PAT = getPAT();
			if (PAT==null) {
				System.out.println("login token has expired, please login again");
				JSONObject resp = new JSONObject();
				resp.put("message", "your login token has expired, please login again");
				return Response.status(401).entity(resp).build();
			}
			ArrayList masterCollections = (ArrayList) CollectionsUtils
					.getMasterCollectionWithREST(getUser(request), PAT, url);
			String firstElem = masterCollections.get(0).toString();
			if (firstElem!=null) {
				if (firstElem.contains("http code 429:")) {
					JSONObject resp = new JSONObject();
					resp.put("message", firstElem);
					return Response.status(429).entity(resp).build();
				}
			}
			
			List curatedCollections = CollectionsUtils.streamLineMasterMap(masterCollections);
			Collections.sort(curatedCollections, mapComparator);
			
			JSONArray ja = convertMapToJSON(curatedCollections);
			System.out.println("curated collectionfor namespace: "+namespace+" kab group: " + group +"="+ ja);
			msg.put("curated collections", ja);

			// make call to kabanero to get current collection

			ApiClient apiClient = KubeUtils.getApiClient();
			CollectionApi api = new CollectionApi(apiClient);
			CollectionList fromKabanero = null;

			try {
				fromKabanero = api.listCollections(namespace, null, null, null);
			} catch (ApiException e) {
				e.printStackTrace();
			}

			List allCollections=CollectionsUtils.allCollections(fromKabanero);
			
			Collections.sort(allCollections, mapComparator);
			msg.put("kabanero collections", convertMapToJSON(allCollections));
			
			System.out.println(" ");
			System.out.println("*** List of all kab collections= "+convertMapToJSON(allCollections));
			System.out.println(" ");
			
			
			try {
				List newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections,
						fromKabanero);
				Collections.sort(newCollections, mapComparator);
				
				List deleletedCollections = (List<Map>) CollectionsUtils
						.filterDeletedCollections(masterCollections, fromKabanero);
				Collections.sort(deleletedCollections, mapComparator);
				
				List versionChangeCollections = (List<Map>) CollectionsUtils
						.filterVersionChanges(masterCollections, fromKabanero);
				Collections.sort(versionChangeCollections, mapComparator);

				ja = convertMapToJSON(newCollections);
				System.out.println("*** new curated collections: " + ja);
				msg.put("new curated collections", ja);

				ja = convertMapToJSON(deleletedCollections);
				System.out.println("*** obsolete collections: " + ja);
				msg.put("obsolete collections", ja);

				ja = convertMapToJSON(versionChangeCollections);
				System.out.println("*** version change collections: " + ja);
				msg.put("version change collections", ja);
			} catch (Exception e) {
				System.out.println("exception cause: " + e.getCause());
				System.out.println("exception message: " + e.getMessage());
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
			JSONObject resp = new JSONObject();
			resp.put("message", e.getMessage());
			return Response.status(500).entity(resp).build();
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
		String workaround = "Please go to the tekton dashboard";
		String route = KubeUtils.getTektonDashboardURL();
		if (!"".equals(route)) {
		    System.out.println(route);
	            workaround += " at " + route;
		}
		workaround += " in your browser and manually configure the webhook";
		if (gituser != null) {
			workaround += " for gituser: " + gituser;
		}

		JSONObject msg = new JSONObject();
		msg.put("message", workaround);

		return Response.ok(msg).build();
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/collections")
	public Response syncCollections(@Context final HttpServletRequest request) {
		// kube call to sync collection
		ApiClient apiClient = null;
		try {
			apiClient = KubeUtils.getApiClient();
		} catch (Exception e) {
			e.printStackTrace();
		}

		CollectionApi api = new CollectionApi(apiClient);
		CollectionList fromKabanero = null;
		try {
			fromKabanero = api.listCollections(namespace, null, null, null);
		} catch (ApiException e) {
			e.printStackTrace();
		}

		List newCollections = null;
		List activateCollections = null;
		List deleletedCollections = null;
		List versionChangeCollections = null;
		Kabanero k = null;
		String collectionsUrl = null;
		JSONObject msg = new JSONObject();
		try {
			
			System.out.println(" ");
			System.out.println("*** List of active kab collections= "+fromKabanero);
			
			String PAT = getPAT();
			if (PAT==null) {
				System.out.println("login token has expired, please login again");
				JSONObject resp = new JSONObject();
				resp.put("message", "your login token has expired, please login again");
				return Response.status(401).entity(resp).build();
			}
			
			k = CollectionsUtils.getKabaneroForNamespace(namespace);
			collectionsUrl = CollectionsUtils.getMasterCollectionUrl(k);
			ArrayList masterCollections = (ArrayList) CollectionsUtils
					.getMasterCollectionWithREST(getUser(request), PAT, collectionsUrl);
			String firstElem = masterCollections.get(0).toString();
			if (firstElem!=null) {
				if (firstElem.contains("http code 429:")) {
					JSONObject resp = new JSONObject();
					resp.put("message", firstElem);
					return Response.status(429).entity(resp).build();
				}
			}
			System.out.println(" ");
			System.out.println("*** List of curated collections= "+masterCollections);
			
			System.out.println(" ");
			System.out.println(" ");

			newCollections = (List<Map>) CollectionsUtils.filterNewCollections(masterCollections, fromKabanero);
			System.out.println("*** new curated collections=" + newCollections);
			System.out.println(" ");
			
			System.out.println(" ");
			System.out.println(" ");
			activateCollections = (List<Map>) CollectionsUtils.filterCollectionsToActivate(masterCollections, fromKabanero);
			System.out.println("*** activate collections=" + activateCollections);
			System.out.println(" ");

			deleletedCollections = (List<Map>) CollectionsUtils.filterDeletedCollections(masterCollections, fromKabanero);
			System.out.println("*** collectionsto delete=" + deleletedCollections);
			System.out.println(" ");

			versionChangeCollections = (List<Map>) CollectionsUtils.filterVersionChanges(masterCollections, fromKabanero);
			System.out.println("*** version Change Collections=" + versionChangeCollections);

		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
			JSONObject resp = new JSONObject();
			resp.put("message", e.getMessage());
			return Response.status(500).entity(resp).build();
		}
		System.out.println("starting refresh");

		// iterate over new collections and create them
		try {
			for (Object o : newCollections) {
				Map m = (Map)o;
				try {
					KabaneroApi kApi = new KabaneroApi(apiClient);
					V1OwnerReference owner = kApi.createOwnerReference(k);
					CollectionSpec spec = new CollectionSpec().version((String)m.get("version")).name((String)m.get("name")).desiredState("active").repositoryUrl(collectionsUrl);
					V1ObjectMeta metadata = new V1ObjectMeta().name((String)m.get("name")).namespace((String)m.get("namespace")).addOwnerReferencesItem(owner);
					Collection c = new Collection().spec(spec).metadata(metadata);
					
					System.out.println("json object for create: " + c.toString());
					api.createCollection(metadata.getNamespace(), c);
					System.out.println("*** collection " + m.get("name") + " created, organization "+group);
					m.put("status", m.get("name") + " created");
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


		// iterate over collections to activate
		try {
			for (Object o : activateCollections) {
				Map m = (Map)o;
				try {
					Collection c = makeCollection(m, namespace, collectionsUrl);
					//JsonObject jo = makeJSONBody(m, namespace);
					System.out.println("json object for activate: " + c.toString());
					api.patchCollection(namespace, c.getMetadata().getName(), c);
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

		// iterate over collections to deactivate
		try {
			for (Object o : deleletedCollections) {
				Map m = (Map)o;
				try {
					Collection c = makeCollection(m, namespace, null);
					System.out.println("json object for deactivate: " + c.toString());
					api.patchCollection(namespace, c.getMetadata().getName(), c);
					System.out.println("*** collection " + m.get("name") + " deactivated, organization "+group);
					m.put("status", m.get("name") + " deactivated");
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** collection " + m.get("name") + " failed to deactivate, organization "+group);
					e.printStackTrace();
					m.put("status", m.get("name") + " deactivation failed");
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
			
			for (Object o : versionChangeCollections) {
				Map m = (Map)o;
				try {
					
					String state = getDesiredState(versionChangeCollections, activateCollections);
					if (state!=null) {
						m.put("desiredState", state);
					}
					Collection c = makeCollection(m, namespace, collectionsUrl);
					System.out.println("json object for version change: " + c.toString());
					api.patchCollection(namespace, c.getMetadata().getName(), c);
					System.out.println(
							"*** " + m.get("name") + "version change completed, new version number: " + m.get("version")+", organization "+group);
					m.put("status", m.get("name") + "version change completed, new version number: " + m.get("version"));
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
			Collections.sort(newCollections, mapComparator);
			Collections.sort(activateCollections, mapComparator);
			Collections.sort(deleletedCollections, mapComparator);
			Collections.sort(versionChangeCollections, mapComparator);
			
			msg.put("new curated collections", convertMapToJSON(newCollections));
			msg.put("activate collections", convertMapToJSON(activateCollections));
			msg.put("obsolete collections", convertMapToJSON(deleletedCollections));
			msg.put("version change collections", convertMapToJSON(versionChangeCollections));
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}
		System.out.println("finishing refresh");
		return Response.ok(msg).build();
	}
	
	private String getDesiredState(List<Map> versionColls, List<Map> activateColls) {
		String state=null;
		if (activateColls!=null) {
			for (Map map : versionColls) {
				String name = (String) map.get("name");
				name = name.trim();
				boolean match = false;
				for (Map map1 : activateColls) {
					String name1 = (String) map1.get("name");
					String desiredState = (String) map1.get("desiredState");
					name1 = name1.trim();
					if (name1.contentEquals(name)) {
						state=desiredState;
					}
				}
			}
		}
		return state;
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
		CollectionApi api = new CollectionApi(apiClient);
		String plural = "collections";

		JSONObject msg = new JSONObject();

		try {
			// mapOneResource(ApiClient apiClient, String group, String version, String plural, String namespace, String name)
			Collection fromKabanero = api.getCollection(namespace, name);
			System.out.println("*** reading collection object: "+fromKabanero);
			if (fromKabanero==null) {
				System.out.println("*** " + "Collection name: " + name + " 404 not found");
				msg.put("status", "Collection name: " + name + " 404 not found");
				return Response.status(400).entity(msg).build();
			}
			String collVersion = fromKabanero.getSpec().getVersion();
			collVersion="0.2.7";
			Map m = new HashMap();
			m.put("name", name);
			m.put("version", collVersion);
			m.put("desiredState","inactive");
			Collection c = makeCollection(m, namespace, null);
			System.out.println("*** json object for deactivate: " + c.toString());
			api.patchCollection(namespace, c.getMetadata().getName(), c);
			System.out.println("*** " + "Collection name: " + name + " deactivated");
			msg.put("status", "Collection name: " + name + " deactivated");
			fromKabanero = api.getCollection(namespace, name);
			System.out.println("*** reading collection object after deactivate: "+fromKabanero);
			return Response.ok(msg).build();
		} catch (ApiException apie) {
			apie.printStackTrace();
			String responseBody = apie.getResponseBody();
			System.err.println("Response body: " + responseBody);
			msg.put("status",
					"Collection name: " + name + " failed to deactivate, exception message: " + apie.getMessage());
			return Response.status(400).entity(msg).build();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.put("status",
					"Collection name: " + name + " failed to deactivate, exception message: " + e.getMessage());
			return Response.status(400).entity(msg).build();
		}

	}

	private Collection makeCollection(Map m, String namespace, String url) {
		System.out.println("making JSONBody: " + m.toString());
		CollectionSpec spec = new CollectionSpec().version(m.get("version").toString()).name(m.get("name").toString()).desiredState(m.get("desiredState").toString()).repositoryUrl(url);
		V1ObjectMeta metadata = new V1ObjectMeta().name(m.get("name").toString()).namespace(namespace);
		Collection c = new Collection().spec(spec).metadata(metadata);
		System.out.println("made JSONBody: " + c);
		return c;
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

	private String getPAT() throws ApiException {
		String PAT = null;
		try {
			PAT = (new PATHelper()).extractGithubAccessTokenFromSubject();
			if (PAT == null) {
				throw new ApiException("login token has expired, please login again");
			}
		} catch (Exception e) {
			System.out.println("login token has expired, please login again");
			throw new ApiException("login token has expired, please login again");
		}
		

		return PAT;
	}

}
