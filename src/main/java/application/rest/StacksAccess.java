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
import io.kubernetes.client.ApiResponse;
import kabasec.PATHelper;
import io.kabanero.v1alpha1.models.KabaneroStatusKabaneroInstance;
import io.kabanero.v1alpha2.client.apis.KabaneroApi;
import io.kabanero.v1alpha2.client.apis.StackApi;
import io.kabanero.v1alpha2.models.Stack;
import io.kabanero.v1alpha2.models.StackList;
import io.kabanero.v1alpha2.models.StackSpec;
import io.kabanero.v1alpha2.models.StackSpecHttps;
import io.kabanero.v1alpha2.models.StackSpecImages;
import io.kabanero.v1alpha2.models.StackSpecPipelines;
import io.kabanero.v1alpha2.models.StackSpecVersions;
import io.kabanero.v1alpha2.models.StackStatus;
import io.kabanero.v1alpha2.models.StackStatusVersions;
import io.kabanero.v1alpha2.models.Kabanero;
import io.kabanero.v1alpha2.models.KabaneroSpecStacks;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksPipelines;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksRepositories;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;
import io.kubernetes.client.models.V1Status;

@RolesAllowed("admin")
@Path("/v1")
public class StacksAccess {

	private static String apiVersion = "kabanero.io/v1alpha2";
	
	private final static String trueStr = "True";

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
			
			System.out.println("Operator ready: "+k.getStatus().getKabaneroInstance().getReady());
			System.out.println("Operator error msg: "+k.getStatus().getKabaneroInstance().getErrorMessage());
			
			if (!trueStr.contentEquals(k.getStatus().getKabaneroInstance().getReady())) {
				JSONObject resp = new JSONObject();
				resp.put("message", "The Kabanero operator is not ready, error message: "+k.getStatus().getKabaneroInstance().getErrorMessage());
				return Response.status(503).entity(resp).build();
			}
			
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
			
			try {
				for (KabaneroSpecStacksRepositories r :  k.getSpec().getStacks().getRepositories()) {
					stacks.addAll( (ArrayList) StackUtils
							.getStackFromGIT(getUser(request), PAT, r));
				}
				String firstElem = stacks.get(0).toString();
				if (firstElem!=null) {
					if (firstElem.contains("http code 429:")) {
						JSONObject resp = new JSONObject();
						resp.put("message", firstElem);
						return Response.status(429).entity(resp).build();
					}
				}
			} catch (NullPointerException npe) {
				JSONObject resp = new JSONObject();
				resp.put("message", "The CLI service could not read the repository URL specification(s) from the Kabanero CR");
				return Response.status(424).entity(resp).build();
			}
			
			
			System.out.println("stacks: "+stacks);
			List curatedStacks = StackUtils.streamLineMasterMap(stacks);
			Collections.sort(curatedStacks, mapComparator);
			List<Map> curatedStacksMaps = StackUtils.packageStackMaps(curatedStacks);
			
			JSONArray ja = convertMapToJSON(curatedStacksMaps);
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
				
				msg.put("repositories",  getRepositories(k));

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
		
		Kabanero kab = null;
		
		kab = StackUtils.getKabaneroForNamespace(namespace);
		
		System.out.println("Operator ready: "+kab.getStatus().getKabaneroInstance().getReady());
		System.out.println("Operator error msg: "+kab.getStatus().getKabaneroInstance().getErrorMessage());
		
		if (!trueStr.contentEquals(kab.getStatus().getKabaneroInstance().getReady())) {
			JSONObject resp = new JSONObject();
			resp.put("message", "The Kabanero operator is not ready, error message: "+kab.getStatus().getKabaneroInstance().getErrorMessage());
			return Response.status(503).entity(resp).build();
		}
		
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
		List<Map> activateStacks = new ArrayList<Map>();
		List<Map> deletedStacks = null;
		List versionChangeCollections = null;
		
		List<Stack> multiVersionNewStacks=null;
		List<Stack> multiVersionActivateStacks=null;
		List<Stack> multiVersionDeletedStacks=null;
		Map versionedStackPipelineMap = new HashMap();
		
		KabaneroSpecStacksPipelines defaultPipeline=null;
		
		List curatedStacks = null;
		
		
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
			
			
			ArrayList<StackSpecPipelines> pipelines = new ArrayList<StackSpecPipelines>();
			try {
				List<KabaneroSpecStacksPipelines> defaultPipelines = kab.getSpec().getStacks().getPipelines();
				for (KabaneroSpecStacksPipelines defaultPipelineElement: defaultPipelines) {
					StackSpecPipelines pipeline = new StackSpecPipelines();
					StackSpecHttps https = new StackSpecHttps();
					https.setUrl(defaultPipelineElement.getHttps().getUrl());
					pipeline.setHttps(https);
					pipeline.setSha256(defaultPipelineElement.getSha256());
					pipeline.setId(defaultPipelineElement.getId());
					pipelines.add(pipeline);
				}}
			catch (NullPointerException npe) {
				JSONObject resp = new JSONObject();
				resp.put("message", "The CLI service could not read the pipeline specification(s) from the Kabanero CR");
				return Response.status(424).entity(resp).build();
			}
			
			
			// multi custom pipelines per repository collection in 060, future, design not set on this yet
			List<KabaneroSpecStacksRepositories> stackRepos = kab.getSpec().getStacks().getRepositories();
			
			
			ArrayList stacks = new ArrayList();
			
			try {
				for (KabaneroSpecStacksRepositories r : kab.getSpec().getStacks().getRepositories()) {

					List stacksFromRest = (ArrayList) StackUtils.getStackFromGIT(getUser(request), PAT, r);
					stacks.addAll(stacksFromRest);

					ArrayList<StackSpecPipelines> stackPipelines = new ArrayList<StackSpecPipelines>(); 
					ArrayList<StackSpecPipelines> tempPipelines = null;
					if (r.getPipelines()!=null && r.getPipelines().size() > 0) {
						for (KabaneroSpecStacksPipelines pipelineElement : r.getPipelines()) {
							StackSpecPipelines stackPipeline = new StackSpecPipelines();
							StackSpecHttps https = new StackSpecHttps();
							https.setUrl(pipelineElement.getHttps().getUrl());
							stackPipeline.setHttps(https);
							stackPipeline.setSha256(pipelineElement.getSha256());
							stackPipeline.setId(pipelineElement.getId());
							stackPipelines.add(stackPipeline);
						}
						tempPipelines = stackPipelines;
					} else {
						tempPipelines =  pipelines;
					}
					
					for (Object o:stacksFromRest) {
						Map m = (Map)o;
						String name = (String) m.get("id");
						versionedStackPipelineMap.put(name, tempPipelines);
					}
					
				}
			} catch (NullPointerException npe) {
				JSONObject resp = new JSONObject();
				resp.put("message", "The CLI service could not read the repository or pipelines URL specification(s) from the Kabanero CR");
				return Response.status(424).entity(resp).build();
			}
			
			System.out.println("versionedStackPipelineMap: "+versionedStackPipelineMap);
			
			
			
			String firstElem = stacks.get(0).toString();
			if (firstElem!=null) {
				if (firstElem.contains("http code 429:")) {
					JSONObject resp = new JSONObject();
					resp.put("message", firstElem);
					return Response.status(429).entity(resp).build();
				}
			}
						
			
			curatedStacks = StackUtils.streamLineMasterMap(stacks);
			Collections.sort(curatedStacks, mapComparator); 
			curatedStacks = StackUtils.packageStackMaps(curatedStacks);
			
			System.out.println(" ");
			System.out.println("*** List of curated stacks= "+curatedStacks);
			
			System.out.println(" ");
			System.out.println(" ");

			newStacks = (List<Map>) StackUtils.filterNewStacks(stacks, fromKabanero);
			Collections.sort(newStacks, mapComparator);
			System.out.println("*** new curated stacks=" + newStacks);
			System.out.println(" ");
			multiVersionNewStacks=(List<Stack>) StackUtils.packageStackObjects(newStacks, versionedStackPipelineMap);
			newStacks = (List<Map>) StackUtils.packageStackMaps(newStacks);
			 
			
			
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
			JSONObject resp = new JSONObject();
			resp.put("message", e.getMessage());
			return Response.status(500).entity(resp).build();
		}
		System.out.println("starting stack SYNC");

		// iterate over new collections and create them
		try {
			for (Stack s  : multiVersionNewStacks) {
				Stack stack=null;
				int i=0;
				Map m=(Map) newStacks.get(i);
				String updateType="";
				try {
					KabaneroApi kApi = new KabaneroApi(apiClient);
					V1OwnerReference owner = kApi.createOwnerReference(kab);
					V1ObjectMeta metadata = new V1ObjectMeta().name((String)s.getSpec().getName()).namespace(namespace).addOwnerReferencesItem(owner);
					s.setMetadata(metadata);
					s.setApiVersion(apiVersion);
					List<StackSpecVersions> kabSpecVersions=StackUtils.getKabInstanceVersions(fromKabanero, s.getSpec().getName());
					if (kabSpecVersions!=null) {
						updateType="patch";
						s.getSpec().getVersions().addAll(kabSpecVersions);
						
						Stack kabStack = StackUtils.getKabInstance(fromKabanero, s.getSpec().getName());
						
						kabStack.getSpec().setVersions(s.getSpec().getVersions());
						
						System.out.println(s.getSpec().getName()+" stack for patch create: " + s.toString());
						stack=api.updateStack(namespace, s.getMetadata().getName(), kabStack);
					} else {
						updateType="create";
						System.out.println(s.getSpec().getName()+" stack for just create: " + s.toString());
						stack=api.createStack(namespace, s);
					}
					System.out.println("*** stack " + s.getSpec().getName() + " created, organization "+group);
					m.put("status", s.getSpec().getName() + " created");
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** stack " + s.getSpec().getName() + " failed to "+updateType+" , organization "+group);
					e.printStackTrace();
					m.put("status", s.getSpec().getName() + " create failed");
					//m.put("exception message", "stack name: "+s.getSpec().getName()+", "+e.getMessage()+", cause: "+e.getCause()+", stack status message: "+stack.getStatus().getStatusMessage());
					System.out.println("stack status message="+stack.getStatus().getStatusMessage());
					m.put("exception message", "stack name: "+s.getSpec().getName()+", "+e.getMessage()+", cause: "+e.getCause());
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
			try {
				fromKabanero = api.listStacks(namespace, null, null, null);
			} catch (ApiException e) {
				e.printStackTrace();
			}
			for (Stack s : fromKabanero.getItems()) {
				ArrayList<Map> versions= new ArrayList<Map>();
				HashMap m = new HashMap();
				ApiResponse apiResponse = null;
				Stack stack=null;
				try {
	
					List<StackSpecVersions> kabStackSpecVersions=s.getSpec().getVersions();
					boolean atLeastOneVersionToActivate=false;
					
					
					for (StackSpecVersions kabStackSpecVersion: kabStackSpecVersions) {
						System.out.println("statusStackVersion: "+kabStackSpecVersion+" name: "+s.getSpec().getName());
			
						if ("inactive".equals(kabStackSpecVersion.getDesiredState())) {
							atLeastOneVersionToActivate=true;
							HashMap versionMap = new HashMap();
							versionMap.put("version", kabStackSpecVersion.getVersion());
							versions.add(versionMap);
							System.out.println("name: "+s.getSpec().getName()+" version="+kabStackSpecVersion.getVersion()+", setting to active");
							kabStackSpecVersion.setDesiredState("active");
						}
					}
					System.out.println("name: "+s.getSpec().getName()+" atLeastOneVersionToActivate="+atLeastOneVersionToActivate);
					s.getSpec().setVersions(kabStackSpecVersions);
					if (atLeastOneVersionToActivate) {
						m.put("versions", versions); 
						m.put("name", s.getSpec().getName());
						activateStacks.add(m);
						System.out.println(s.getSpec().getName()+", activate with stack: " + s.toString());
						stack=api.updateStack(namespace, s.getMetadata().getName(), s);
					    m.put("status", s.getSpec().getName() + " activated");
						System.out.println("*** status: "+s.getMetadata().getName()+" versions(s): "+versions + " activated");
					}
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** stack " + s.getSpec().getName() + " failed to activate, organization "+group);
					System.out.println("*** stack object: "+s.toString());
					e.printStackTrace();
					m.put("status", "failed to activate");
					String message = "stack name: "+s.getSpec().getName()+", "+e.getMessage()+", cause: "+e.getCause();
					System.out.println("stack status message="+stack.getStatus().getStatusMessage());
					System.out.println("message="+message);
					m.put("exception message", message);
				}
				
			}
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}

		// iterate over collections to delete
		System.out.println("Starting DELETE processing");
		try {
			deletedStacks = new ArrayList<Map>();
			for (Stack kabStack : fromKabanero.getItems()) {
				ArrayList<Map> versions= new ArrayList<Map>();
				HashMap m = new HashMap();
				V1Status v1status=null;
				Stack stack=null;
				Object o=null;
				try {

					List<StackSpecVersions> stackSpecVersions = new ArrayList<StackSpecVersions>();


					List<StackSpecVersions> kabStackVersions=kabStack.getSpec().getVersions();
					boolean atLeastOneToDelete=false;

					System.out.println("Kab stack versions: "+kabStackVersions);

					for (StackSpecVersions stackSpecVersion:kabStackVersions) {
						System.out.println("statusStackVersion: "+stackSpecVersion.getVersion()+" name: "+kabStack.getSpec().getName());

						boolean isVersionInGitStack=StackUtils.isStackVersionInGit(curatedStacks, stackSpecVersion.getVersion(), kabStack.getSpec().getName());
						System.out.println("isVersionInGitStack: "+isVersionInGitStack);
						if (isVersionInGitStack) {
							System.out.println("Version: "+stackSpecVersion.getVersion()+" is in GIT so keep it in the specversions list: ");
							StackSpecVersions specVersion = new StackSpecVersions();
							specVersion.setDesiredState("active");
							specVersion.setVersion(stackSpecVersion.getVersion());
							specVersion.setImages(stackSpecVersion.getImages());
							specVersion.setPipelines((List<StackSpecPipelines>) versionedStackPipelineMap.get(kabStack.getSpec().getName()));
							stackSpecVersions.add(specVersion);
						} else {
							atLeastOneToDelete=true;
							HashMap versionMap = new HashMap();
							versionMap.put("version", stackSpecVersion.getVersion());
							versions.add(versionMap);
						}
					}

					kabStack.getSpec().setVersions(stackSpecVersions);
					m.put("name", kabStack.getSpec().getName());


					m.put("versions", versions);

					System.out.println("name: "+kabStack.getSpec().getName()+" atLeastOneVersionToDelete="+atLeastOneToDelete);

					if (atLeastOneToDelete) {
						deletedStacks.add(m);
						// if there is more than one version in the stack and the number of versions to delete is not equal to the total number of versions
						if (kabStackVersions.size() > 1  &&  versions.size()!=kabStackVersions.size()) {
							System.out.println(kabStack.getSpec().getName()+" delete stack versions deleted: "+versions+" through omission, stack: "+kabStack);
							stack=api.updateStack(namespace, kabStack.getSpec().getName(), kabStack);
						} else {
							String name = kabStack.getSpec().getName();
							String version = kabStackVersions.get(0).getVersion();
							System.out.println("delete single stack: "+name+", version number: "+version);
							V1DeleteOptions deleteOptions = new V1DeleteOptions();
							deleteOptions.setGracePeriodSeconds((long)3);
							deleteOptions.setOrphanDependents(true);
							deleteOptions.setKind("stacks");
							deleteOptions.setApiVersion(apiVersion);
							v1status=api.deleteStack(namespace, kabStack.getSpec().getName(), deleteOptions, 0, true, "");
							
//							int rc = KubeUtils.deleteKubeResource(apiClient, namespace, name, group, version, "stacks");
//							if (rc == 0) {
//								System.out.println("*** " + "Stack name: " + name + " deleted");
//								msg.put("status", "Stack name: " + name + " deleted");
//								return Response.ok(msg).build();
//							}
//							else if (rc == 404) {
//								System.out.println("*** " + "Stack name: " + name + " 404 not found");
//								msg.put("status", "Stack name: " + name + " 404 not found");
//								return Response.status(400).entity(msg).build();
//							} else {
//								System.out.println("*** " + "Stack name: " + name + " was not deleted, rc="+rc);
//								msg.put("status", "Stack name: " + name + " was not deleted, rc="+rc);
//								return Response.status(400).entity(msg).build();
//							}
						}
						System.out.println("*** status: "+kabStack.getMetadata().getName()+" versions(s): "+versions + " deleted");
					} else {
						System.out.println("Skipping: "+kabStack.getSpec().getName()+" nothing to delete");
					}
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** stack " + kabStack.getSpec().getName() + " failed to delete, organization "+group);
					System.out.println("*** stack object: "+kabStack.toString());
					e.printStackTrace();
					m.put("status", "failed to delete");
					String statusMsg = null;
					if (stack == null && v1status!=null) {
						statusMsg = v1status.getMessage();
					}
					else { 
						statusMsg = stack.getStatus().getStatusMessage();
					}
					
					System.out.println("status message="+statusMsg);
					m.put("exception message", e.getMessage()+", cause: "+e.getCause());
				}

			}
		} catch (Exception e) {
			System.out.println("exception cause: " + e.getCause());
			System.out.println("exception message: " + e.getMessage());
			e.printStackTrace();
		}


		JSONArray newStacksJA = convertMapToJSON(newStacks);
		JSONArray activateStacksJA = convertMapToJSON(activateStacks);
		JSONArray deletedStacksJA = convertMapToJSON(deletedStacks);

		
		
		// log successful changes too!
		System.out.println("new curated stacks: "+newStacksJA);
		System.out.println("activated stacks: "+activateStacksJA);
		System.out.println("obsolete stacks: "+deletedStacks);
		try {
			msg.put("new curated stacks", newStacksJA);
			msg.put("activate stacks", activateStacksJA);
			msg.put("obsolete stacks", deletedStacksJA);

			msg.put("repositories", getRepositories(kab));
			
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
	
	private JSONArray getRepositories(Kabanero kab) {
		JSONArray repoJA = new JSONArray();
		
		for (KabaneroSpecStacksRepositories repo: kab.getSpec().getStacks().getRepositories()) {
			JSONObject jo = new JSONObject();
			jo.put("name", repo.getName());
			jo.put("url", repo.getHttps().getUrl());
			repoJA.add(jo);
		}
		return repoJA;
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
	@Path("/stacks/{name}/versions/{version}")
	public Response deActivateStack(@Context final HttpServletRequest request,
			@PathParam("name") final String name, @PathParam("version") final String version) throws Exception {
		// make call to kabanero to delete collection
		
		Kabanero kab = StackUtils.getKabaneroForNamespace(namespace);
		
		System.out.println("Operator ready: "+kab.getStatus().getKabaneroInstance().getReady());
		System.out.println("Operator error msg: "+kab.getStatus().getKabaneroInstance().getErrorMessage());
		
		if (!trueStr.contentEquals(kab.getStatus().getKabaneroInstance().getReady())) {
			JSONObject resp = new JSONObject();
			resp.put("message", "The Kabanero operator is not ready, error message: "+kab.getStatus().getKabaneroInstance().getErrorMessage());
			return Response.status(503).entity(resp).build();
		}
		
		
		ApiClient apiClient = KubeUtils.getApiClient();
		StackApi api = new StackApi(apiClient);
		String plural = "stacks";

		JSONObject msg = new JSONObject();

		try {
			StackList fromKabanero = null;
			try {
				fromKabanero = api.listStacks(namespace, null, null, null);
			} catch (ApiException e) {
				e.printStackTrace();
			}
			
			Stack kabStack = api.getStack(namespace, name);
			System.out.println("*** reading stack object: "+kabStack);
			if (kabStack==null) {
				System.out.println("*** " + "Stack name: " + name + " 404 not found");
				msg.put("status", "Stack name: " + name + " 404 not found");
				return Response.status(400).entity(msg).build();
			}
			List<StackSpecVersions> kabSpecVersions=null;
			if (version!=null) {
				kabSpecVersions=StackUtils.getKabInstanceVersions(fromKabanero, name);
				boolean verMatch=false;
				for (StackSpecVersions versionFromKab:kabSpecVersions) {
					if (version.contentEquals(versionFromKab.getVersion())) {
						versionFromKab.setDesiredState("inactive");
						verMatch=true;
					}
				}
				if (!verMatch) {
					System.out.println("*** " + "Version: "+version+" not found in Stack name: " + name);
					msg.put("status", "Version: "+version+" not found in Stack name: " + name);
					return Response.status(400).entity(msg).build();
				}
			} else {
				System.out.println("no version number supplied for stack: "+name);
				msg.put("status", "no version number supplied for stack: "+name);
				return Response.status(400).entity(msg).build();

			}
			System.out.println(kabStack.getSpec().getName()+" stack for patch deactivate: " + kabStack.toString());
			kabStack.getSpec().setVersions(kabSpecVersions);
			api.patchStack(namespace, kabStack.getMetadata().getName(), kabStack);
			System.out.println("*** " + "Stack name: " + name + " deactivated");
			msg.put("status", "Stack name: " + name + " version: "+version+" deactivated");
			msg.put("repositories", getRepositories(kab));
			return Response.ok(msg).build();
		} catch (ApiException apie) {
			apie.printStackTrace();
			String responseBody = apie.getResponseBody();
			System.err.println("Response body: " + responseBody);
			msg.put("status",
					"Stack name: " + name + " version: "+version+" failed to deactivate, exception message: " + apie.getMessage());
			msg.put("exception message", apie.getMessage()+", cause: "+apie.getCause());
			return Response.status(400).entity(msg).build();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.put("status",
					"Stack name: " + name + " version: "+version+" failed to deactivate, exception message: " + e.getMessage());
			msg.put("exception message", e.getMessage()+", cause: "+e.getCause());
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
