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

import io.kabanero.v1alpha2.client.apis.KabaneroApi;
import io.kabanero.v1alpha2.client.apis.StackApi;
import io.kabanero.v1alpha2.models.Stack;
import io.kabanero.v1alpha2.models.StackList;
import io.kabanero.v1alpha2.models.StackSpec;
import io.kabanero.v1alpha2.models.StackSpecHttps;
import io.kabanero.v1alpha2.models.StackSpecPipelines;
import io.kabanero.v1alpha2.models.StackSpecVersions;
import io.kabanero.v1alpha2.models.StackStatus;
import io.kabanero.v1alpha2.models.Kabanero;
import io.kabanero.v1alpha2.models.KabaneroSpecStacks;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksPipelines;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksRepositories;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;

@RolesAllowed("admin")
@Path("/v1")
public class StacksAccess {

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
			image=StackUtils.getImage(namespace);
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
	@Path("/stacks")
	public Response listStacks(@Context final HttpServletRequest request) {
		JSONObject msg = new JSONObject();
		try {
			
			System.out.println("Entering listStacks, namespace =" + namespace);
			
			Kabanero k = StackUtils.getKabaneroForNamespace(namespace);
			
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
			
			ArrayList stacks = new ArrayList();
			
			for (KabaneroSpecStacksRepositories r :  k.getSpec().getStacks().getRepositories()) {
				stacks.addAll( (ArrayList) StackUtils
						.getStackFromGIT(getUser(request), PAT, r.getHttps().getUrl()));
			}
			String firstElem = stacks.get(0).toString();
			if (firstElem!=null) {
				if (firstElem.contains("http code 429:")) {
					JSONObject resp = new JSONObject();
					resp.put("message", firstElem);
					return Response.status(429).entity(resp).build();
				}
			}
			
			
			System.out.println("stacks: "+stacks);
			List curatedStacks = StackUtils.streamLineMasterMap(stacks);
			Collections.sort(curatedStacks, mapComparator);
			
			JSONArray ja = convertMapToJSON(curatedStacks);
			System.out.println("curated stack for namespace: "+namespace+" kab group: " + group +"="+ ja);
			msg.put("curated stacks", ja);

			// make call to kabanero to get current collection

			ApiClient apiClient = KubeUtils.getApiClient();
			StackApi api = new StackApi(apiClient);
			StackList fromKabanero = null;

			try {
				fromKabanero = api.listStacks(namespace, null, null, null);
			} catch (ApiException e) {
				e.printStackTrace();
			}
			
			System.out.println("kabanero instance stacks:"+fromKabanero);
			
			List kabStacks=StackUtils.allStacks(fromKabanero);
			
			Collections.sort(kabStacks, mapComparator);
			JSONArray allKabStacksJSON = convertMapToJSON(kabStacks);
			msg.put("kabanero stacks", allKabStacksJSON);
			
			System.out.println(" ");
			System.out.println("*** List of all kab collections= "+allKabStacksJSON);
			System.out.println(" ");
			
			
			try {
				List newStacks = (List<Map>) StackUtils.filterNewStacks(stacks,
						fromKabanero);
				Collections.sort(newStacks, mapComparator);
				
				newStacks = StackUtils.packageStackMaps(newStacks);
				
				List deleletedStacks = (List<Map>) StackUtils
						.filterDeletedStacks(stacks, fromKabanero);
				Collections.sort(deleletedStacks, mapComparator);
				deleletedStacks = StackUtils.packageStackMaps(deleletedStacks);

				ja = convertMapToJSON(newStacks);
				System.out.println("*** new curated stacks: " + ja);
				msg.put("new curated stacks", ja);

				ja = convertMapToJSON(deleletedStacks);
				System.out.println("*** obsolete stacks: " + ja);
				msg.put("obsolete stacks", ja);

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
	@Path("/stacks")
	public Response syncStacks(@Context final HttpServletRequest request) {
		
		System.out.println("Entering syncStacks, namespace =" + namespace);
		
		// kube call to sync collection
		ApiClient apiClient = null;
		try {
			apiClient = KubeUtils.getApiClient();
		} catch (Exception e) {
			e.printStackTrace();
		}

		StackApi api = new StackApi(apiClient);
		StackList fromKabanero = null;
		try {
			fromKabanero = api.listStacks(namespace, null, null, null);
		} catch (ApiException e) {
			e.printStackTrace();
		}

		List newStacks = null;
		List activateStacks = null;
		List deleletedStacks = null;
		List versionChangeCollections = null;
		
		List<Stack> multiVersionNewStacks=null;
		List<Stack> multiVersionActivateStacks=null;
		List<Stack> multiVersionDeletedStacks=null;
		
		KabaneroSpecStacksPipelines defaultPipeline=null;
		
		List curatedStacks = null;
		
		Kabanero kab = null;
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
			
			kab = StackUtils.getKabaneroForNamespace(namespace);
			
			List<KabaneroSpecStacksPipelines> defaultPipelines = kab.getSpec().getStacks().getPipelines();
			ArrayList<StackSpecPipelines> pipelines = new ArrayList<StackSpecPipelines>();
			
			for (KabaneroSpecStacksPipelines defaultPipelineElement: defaultPipelines) {
				StackSpecPipelines pipeline = new StackSpecPipelines();
				StackSpecHttps https = new StackSpecHttps();
				https.setUrl(defaultPipelineElement.getHttps().getUrl());
				pipeline.setHttps(https);
				pipeline.setSha256(defaultPipelineElement.getSha256());
				pipeline.setId(defaultPipelineElement.getId());
				pipelines.add(pipeline);
			}
			
			
			// multi custom pipelines per repository collection in 060, future, design not set on this yet
			List<KabaneroSpecStacksRepositories> stackRepos = kab.getSpec().getStacks().getRepositories();
			
			Map versionedStackPipelineMap = new HashMap();
			ArrayList stacks = new ArrayList();
			
			for (KabaneroSpecStacksRepositories r : kab.getSpec().getStacks().getRepositories()) {
				
				List stacksFromRest = (ArrayList) StackUtils.getStackFromGIT(getUser(request), PAT, r.getHttps().getUrl());
				stacks.addAll(stacksFromRest);
				
				versionedStackPipelineMap.put(r.getName(),stacksFromRest);
				
				ArrayList<StackSpecPipelines> stackPipelines = new ArrayList<StackSpecPipelines>();
				for (KabaneroSpecStacksPipelines pipelineElement: r.getPipelines()) {
					StackSpecPipelines stackPipeline = new StackSpecPipelines();
					StackSpecHttps https = new StackSpecHttps();
					https.setUrl(pipelineElement.getHttps().getUrl());
					stackPipeline.setHttps(https);
					stackPipeline.setSha256(pipelineElement.getSha256());
					stackPipeline.setId(pipelineElement.getId());
					stackPipelines.add(stackPipeline);
				}
				if ( stackPipelines.size() > 0 ) {
					versionedStackPipelineMap.put(r.getName(), stackPipelines);
				} else {
					versionedStackPipelineMap.put(r.getName(), pipelines);
				}
			}
			
			
			
			String firstElem = stacks.get(0).toString();
			if (firstElem!=null) {
				if (firstElem.contains("http code 429:")) {
					JSONObject resp = new JSONObject();
					resp.put("message", firstElem);
					return Response.status(429).entity(resp).build();
				}
			}
						
			
			List stackMaps = curatedStacks = StackUtils.streamLineMasterMap(stacks);
			Collections.sort(curatedStacks, mapComparator); 
			curatedStacks = StackUtils.packageStackMaps(curatedStacks);
			
			System.out.println(" ");
			System.out.println("*** List of curated stacks= "+stacks);
			
			System.out.println(" ");
			System.out.println(" ");

			newStacks = (List<Map>) StackUtils.filterNewStacks(stackMaps, fromKabanero);
			Collections.sort(newStacks, mapComparator);
			System.out.println("*** new curated stacks=" + newStacks);
			System.out.println(" ");
			newStacks = (List<Map>) StackUtils.packageStackMaps(newStacks);
			multiVersionNewStacks=(List<Stack>) StackUtils.packageStackObjects(newStacks, versionedStackPipelineMap);   
			
			System.out.println(" ");
			System.out.println(" ");
			activateStacks = (List<Map>) StackUtils.filterStacksToActivate(stackMaps, fromKabanero);
			Collections.sort(activateStacks, mapComparator);
			System.out.println("*** activate stacks=" + activateStacks);
			System.out.println(" ");
			activateStacks = (List<Map>) StackUtils.packageStackMaps(activateStacks);
			multiVersionActivateStacks=(List<Stack>) StackUtils.packageStackObjects(activateStacks, versionedStackPipelineMap);

			deleletedStacks = (List<Map>) StackUtils.filterDeletedStacks(stackMaps, fromKabanero);
			Collections.sort(deleletedStacks, mapComparator);
			System.out.println("*** stacks to delete=" + deleletedStacks);
			System.out.println(" ");
			deleletedStacks = (List<Map>) StackUtils.packageStackMaps(deleletedStacks);
			multiVersionDeletedStacks=(List<Stack>) StackUtils.packageStackObjects(deleletedStacks, versionedStackPipelineMap);
			
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
			for (Stack s  : multiVersionNewStacks) {
				int i=0;
				Map m=(Map) newStacks.get(i);
				try {
					KabaneroApi kApi = new KabaneroApi(apiClient);
					V1OwnerReference owner = kApi.createOwnerReference(kab);
					owner.setKind(kab.getKind());
					owner.setApiVersion(kab.getApiVersion());
					owner.setName(kab.getMetadata().getName());
					owner.setController(true);
					owner.setUid(kab.getMetadata().getUid());
					V1ObjectMeta metadata = new V1ObjectMeta().name((String)kab.getMetadata().getName()).namespace((String)m.get("namespace")).addOwnerReferencesItem(owner);
					System.out.println("Stack for create: " + s.toString());
					s.setMetadata(metadata);
					api.createStack(namespace, s);
					System.out.println("*** collection " + s.getSpec().getName() + " created, organization "+group);
					m.put("status", s.getSpec().getName() + " created");
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** collection " + m.get("name") + " failed to activate, organization "+group);
					e.printStackTrace();
					m.put("status", m.get("name") + " activation failed");
					m.put("exception", e.getMessage());
				}
				i++;
			}
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}


		// iterate over collections to activate
		try {
			for (Stack s : multiVersionActivateStacks) {
				int i=0;
				Map m=(Map) activateStacks.get(i);
				try {
					System.out.println("json object for activate: " + s.toString());
					api.patchStack(namespace, s.getMetadata().getName(), s);
					System.out.println("*** stack " + m.get("name") + " activated, organization "+group);
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

		// iterate over collections to delete
		try {
			
			for (Stack s : multiVersionDeletedStacks) {
				int i=0;
				Map m=(Map) deleletedStacks.get(i);
				try {
					System.out.println("object for delete: " + s.toString());
					if (s.getSpec().getVersions().size()==1) {
						api.deleteStack(namespace, s.getSpec().getName(), null, null, null, null);
					} else {
						List<StackSpecVersions> stackSpecVersions=new ArrayList<StackSpecVersions>();
						Stack stack = new Stack();
						StackSpec stackSpec = new StackSpec();
						stackSpec.setVersions(stackSpecVersions);
						stack.setSpec(stackSpec);
						Map saveMap = null;
						for (Object object : curatedStacks) {
							Map map = (Map) object;
							String name=(String) map.get("name");
							if (name.contentEquals((String) m.get("name"))) {
								saveMap=map;
							}
						}
						List<String> versions = (List<String>) saveMap.get("versions");
						for (String version:versions) {
							StackSpecVersions specVersion = new StackSpecVersions();
							specVersion.setDesiredState("active");
							specVersion.setVersion(version);
							stackSpecVersions.add(specVersion);
						}
						stack.setSpec(stackSpec);
						api.patchStack(namespace, s.getSpec().getName(), stack);
					}
					String versionList="";
					for (StackSpecVersions stackSpecVersion:s.getSpec().getVersions()) {
						versionList=versionList+" "+stackSpecVersion;
					}
					System.out.println("*** stack " + m.get("name") + " versions: "+versionList+" deleted, organization "+group);
					m.put("status", m.get("name") + " versions: "+versionList+" deleted");
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** collection " + m.get("name") + " failed to deactivate, organization "+group);
					e.printStackTrace();
					m.put("status", m.get("name") + " deactivation failed");
					m.put("exception", e.getMessage());
				}
				i++;
			}
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}



		// log successful changes too!
		try {
			msg.put("new curated collections", convertMapToJSON(newStacks));
			msg.put("activate collections", convertMapToJSON(activateStacks));
			msg.put("obsolete collections", convertMapToJSON(deleletedStacks));
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
	@Path("/collections/{name}/versions/{version}")
	public Response deActivateStack(@Context final HttpServletRequest request,
			@PathParam("name") final String name, @PathParam("version") final String version) throws Exception {
		// make call to kabanero to delete collection
		ApiClient apiClient = KubeUtils.getApiClient();
		StackApi api = new StackApi(apiClient);
		String plural = "stacks";

		JSONObject msg = new JSONObject();

		try {
			// mapOneResource(ApiClient apiClient, String group, String version, String plural, String namespace, String name)
			Stack fromKabanero = api.getStack(namespace, name);
			System.out.println("*** reading collection object: "+fromKabanero);
			if (fromKabanero==null) {
				System.out.println("*** " + "Collection name: " + name + " 404 not found");
				msg.put("status", "Collection name: " + name + " 404 not found");
				return Response.status(400).entity(msg).build();
			}
		
			Map m = new HashMap();
			m.put("name", name);
			m.put("version", version);
			m.put("desiredState","inactive");
			Stack s = makeStack(m, namespace, null);
			System.out.println("*** object for deactivate: " + s.toString());
			api.patchStack(namespace, s.getMetadata().getName(), s);
			System.out.println("*** " + "Collection name: " + name + " deactivated");
			msg.put("status", "Stack name: " + name + "version: "+version+" deactivated");
			fromKabanero = api.getStack(namespace, name);
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

	private Stack makeStack(Map m, String namespace, String url) {
		ArrayList<StackSpecVersions> versions = new ArrayList<StackSpecVersions>();
		String name = (String) m.get("name");
		StackSpecVersions specVersion = new StackSpecVersions();
		specVersion.setDesiredState((String) m.get("desiredState"));
		specVersion.setVersion((String) m.get("version"));
		versions.add(specVersion);
		StackSpec stackSpec = new StackSpec();
		stackSpec.setVersions(versions);
		stackSpec.setName(name);
		Stack stackObj = new Stack();
		stackObj.setSpec(stackSpec);
		return stackObj;
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
