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
import java.util.StringTokenizer;

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
import io.kabanero.v1alpha2.models.StackSpecImages;
import io.kabanero.v1alpha2.models.StackSpecVersions;
import io.kabanero.v1alpha2.models.StackStatus;
import io.kabanero.v1alpha2.models.StackStatusVersions;
import io.kabanero.v1alpha2.models.Kabanero;
import io.kabanero.v1alpha2.models.KabaneroSpecStacks;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksGitRelease;
import io.kabanero.v1alpha2.models.KabaneroSpecStacksHttps;
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
		return Response.ok(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/operator")
	public Response operatorStatus(@Context final HttpServletRequest request) {
		Kabanero k = StackUtils.getKabaneroForNamespace(namespace);
		String msg = "The kabanero operator is not ready";
		if (trueStr.contentEquals(k.getStatus().getKabaneroInstance().getReady())) {
			msg = "The kabanero operator is ready";
		}
		JSONObject resp = new JSONObject();
		resp.put("message",
				msg + ", The Kabanero operator status is: " + k.getStatus().getKabaneroInstance().getMessage());
		return Response.ok(msg).entity(resp).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
	}
	
	private List getCuratedStacks(HttpServletRequest request, String PAT) {
		ArrayList stacks = new ArrayList();
		boolean failure=false;
		try {
			Kabanero k = StackUtils.getKabaneroForNamespace(namespace);
			for (KabaneroSpecStacksRepositories r :  k.getSpec().getStacks().getRepositories()) {
				stacks.addAll( (ArrayList) StackUtils
						.getStackFromGIT(getUser(request), PAT, r, namespace));
			}
			String firstElem = stacks.get(0).toString();
			if (firstElem!=null) {
				if (firstElem.contains("HTTP Code 429:")) {
					JSONObject resp = new JSONObject();
					resp.put("message", firstElem);
					stacks.add(resp);
				}
			}
		} catch (RuntimeException ex) {
			ex.printStackTrace();
			JSONObject resp = new JSONObject();
			System.out.println("Exception reading stack hub indexes, exception message: "+ex.getMessage()+", cause: "+ex.getCause());
			resp.put("message", "The CLI service could not read the repository URL specification(s) from the Kabanero CR");
			stacks.add(resp);
			failure=true;
		} catch (Exception ex) {
			ex.printStackTrace();
			JSONObject resp = new JSONObject();
			String message = "Exception reading stack hub indexes, exception message: "+ex.getMessage()+", cause: "+ex.getCause();
			System.out.println(message);
			resp.put("message", message);
			stacks.add(resp);
			failure=true;
		}
		
		System.out.println("stacks: "+stacks);
		if (failure) {
			return stacks;
		}
		List curatedStacks = StackUtils.streamLineMasterMap(stacks);
		Collections.sort(curatedStacks, mapComparator);
		System.out.println("curatedStacks (after sort): "+curatedStacks);
		return stacks;
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
				resp.put("message", "your login token has expired or your credentials are invalid, please login again");
				return Response.status(401).entity(resp).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
			}
			
			ArrayList stacks = new ArrayList();
			
			List curatedStacks = getCuratedStacks(request, PAT);
			Object o = curatedStacks.get(0);
			if (o instanceof JSONObject) {
				JSONObject element = (JSONObject) o;
				String message = (String) element.get("message");
				if (message.contains("HTTP Code 429:")) {
					return Response.status(429).entity(element).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
				} else {
					return Response.status(424).entity(element).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
				}
			}
			
			List<Map> curatedStacksMaps = StackUtils.packageStackMaps(curatedStacks);
			System.out.println("curatedStacksMap (after packaging): "+curatedStacksMaps);
			
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
			
			
			
			List kabStacks=StackUtils.allStacks(fromKabanero, namespace, curatedStacks);
			
			System.out.println("kabanero instance stacks before sort:"+kabStacks);
			
			Collections.sort(kabStacks, mapComparator);
			JSONArray allKabStacksJSON = convertMapToJSON(kabStacks);
			msg.put("kabanero stacks", allKabStacksJSON);
			
			System.out.println(" ");
			System.out.println("*** List of all kab collections= "+allKabStacksJSON);
			System.out.println(" ");
			
			
			try {
				List newStacks = (List<Map>) StackUtils.filterNewStacks(curatedStacks,
						fromKabanero);
				Collections.sort(newStacks, mapComparator);
				
				newStacks = StackUtils.packageStackMaps(newStacks);
				
				List deleletedStacks = StackUtils.obsoleteStacks(fromKabanero, curatedStacksMaps);
				

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
			return Response.status(500).entity(resp).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
		}
		return Response.ok(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
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

		return Response.ok(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/stacks")
	public Response syncStacks(@Context final HttpServletRequest request) {
		
		System.out.println("Entering syncStacks, namespace =" + namespace);
		
		Kabanero kab = StackUtils.getKabaneroForNamespace(namespace);
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
		
		List<Stack> multiVersionNewStacks=null;
		
		Map versionedStackPipelineMap = new HashMap();
		
		List curatedStacks = null;
		
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
			
			
			ArrayList<KabaneroSpecStacksPipelines> pipelines = new ArrayList<KabaneroSpecStacksPipelines>();

			List<KabaneroSpecStacksPipelines> defaultPipelines = kab.getSpec().getStacks().getPipelines();
			if (defaultPipelines != null) {
				for (KabaneroSpecStacksPipelines defaultPipelineElement : defaultPipelines) {
					System.out.println("defaultPipelineElement: "+defaultPipelineElement.toString());
					pipelines.add(defaultPipelineElement);
				}
			}
			
			System.out.println("default pipelines size="+pipelines.size());

			// multi custom pipelines per repository collection in 060, future, design not
			// set on this yet.
			List<KabaneroSpecStacksRepositories> stackRepos = kab.getSpec().getStacks().getRepositories();
			
			
			ArrayList stacks = new ArrayList();
			boolean foundOneCustomPipeline = false;
			if (stackRepos!=null) {
				for (KabaneroSpecStacksRepositories r : stackRepos) {

					List stacksFromRest = (ArrayList) StackUtils.getStackFromGIT(getUser(request), PAT, r, namespace);
					stacks.addAll(stacksFromRest);

					ArrayList<KabaneroSpecStacksPipelines> stackPipelines = new ArrayList<KabaneroSpecStacksPipelines>(); 
					ArrayList<KabaneroSpecStacksPipelines> tempPipelines = null;
					if (r.getPipelines()!=null && r.getPipelines().size() > 0) {
						for (KabaneroSpecStacksPipelines pipelineElement : r.getPipelines()) {
								System.out.println("pipelineElement: "+pipelineElement.toString());
								stackPipelines.add(pipelineElement);
								foundOneCustomPipeline=true;
						}
						tempPipelines = stackPipelines;
					} else {
						tempPipelines =  pipelines;
					}
					
					for (Object o:stacksFromRest) {
						Map m = (Map)o;
						String name = (String) m.get("id");
						String version = (String) m.get("version");
						versionedStackPipelineMap.put(name+"-"+version, tempPipelines);
					}
					
				}
			} else {
				JSONObject resp = new JSONObject();
				resp.put("message", "The CLI service could not read the repository URL specification(s) from the Kabanero CR");
				return Response.status(424).entity(resp).build();
			}
			
			if (foundOneCustomPipeline==false && pipelines.size() == 0) {
				JSONObject resp = new JSONObject();
				resp.put("message", "The CLI service could not read the pipeline specification(s) from the Kabanero CR");
				return Response.status(424).entity(resp).build();
			} 
			
			
			
			System.out.println("versionedStackPipelineMap: "+versionedStackPipelineMap);
			
			
			
			String firstElem = stacks.get(0).toString();
			if (firstElem!=null) {
				if (firstElem.contains("HTTP Code 429:")) {
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
			return Response.status(500).entity(resp).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
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
							specVersion.setPipelines((List<KabaneroSpecStacksPipelines>) versionedStackPipelineMap.get(kabStack.getSpec().getName()));
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
							stack=api.deleteStack(namespace, kabStack.getSpec().getName(), null, 0, true, "");
						}
						System.out.println("*** status: "+kabStack.getMetadata().getName()+" versions(s): "+versions + " deleted");
					} else {
						System.out.println("Skipping: "+kabStack.getSpec().getName()+" nothing to delete");
					}
				} catch (Exception e) {
					System.out.println("exception cause: " + e.getCause());
					System.out.println("exception message: " + e.getMessage());
					System.out.println("*** stack " + kabStack.getSpec().getName() + " failed to delete, organization "+group);
					System.out.println("*** stack status message: "+stack.getStatus().getStatusMessage());
					System.out.println("*** stack object: "+kabStack.toString());
					e.printStackTrace();
					m.put("status", "failed to delete");
					String statusMsg = null;
					if (stack!=null) {
						statusMsg = stack.getStatus().getStatusMessage();
					} else {
						System.out.println("no status message");
					}
					m.put("exception message", e.getMessage()+", cause: "+e.getCause()+", stack status message: "+statusMsg);
				}

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
							if (!StackUtils.isStackVersionDeleted(deletedStacks, s.getSpec().getName(),
									kabStackSpecVersion.getVersion())) {
								atLeastOneVersionToActivate = true;
								HashMap versionMap = new HashMap();
								versionMap.put("version", kabStackSpecVersion.getVersion());
								versions.add(versionMap);
								System.out.println("name: " + s.getSpec().getName() + " version="
										+ kabStackSpecVersion.getVersion() + ", setting to active");
								kabStackSpecVersion.setDesiredState("active");
							}
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
		return Response.ok(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
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
		System.out.println("entering getRepositories(Kabanero kab) ");
		System.out.println("kab.getSpec().getStacks().getRepositories().toString(): "+kab.getSpec().getStacks().getRepositories().toString());
		for (KabaneroSpecStacksRepositories repo: kab.getSpec().getStacks().getRepositories()) {
			System.out.println("iterating in for loop in getRepositories(Kabanero kab)");
			JSONObject jo = new JSONObject();
			String url="not resolved";
			KabaneroSpecStacksHttps kabaneroSpecStacksHttps = repo.getHttps();
			if (kabaneroSpecStacksHttps!=null) {
				url = repo.getHttps().getUrl();
			} else {
				KabaneroSpecStacksGitRelease gitRelease= repo.getGitRelease();
				System.out.println("repo.getGitRelease()="+repo.getGitRelease());
				if (gitRelease!=null) {
					url = "https://"+repo.getGitRelease().getHostname()+"/"+repo.getGitRelease().getOrganization()+"/"+repo.getGitRelease().getProject()+"/"+repo.getGitRelease().getAssetName();
				} 
				else {
					url="not resolved";
				}
			}
			if (!"not resolved".contentEquals(url)) {
				jo.put("name", repo.getName());
				jo.put("url", url);
				repoJA.add(jo);
			}
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
	
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/describe/stacks/{name}/versions/{version}")
	public Response describeStack(@Context final HttpServletRequest request,
			@PathParam("name") final String name, @PathParam("version") final String version) throws Exception {
		System.out.println("In describe stack");
		List appNames = new ArrayList<String>();
		Kabanero kab = StackUtils.getKabaneroForNamespace(namespace);

		ApiClient apiClient = KubeUtils.getApiClient();
		
		try {
			List deployments = KubeUtils.listResources2(apiClient, group, apiVersion, "deployments",namespace);
			for (Object obj: deployments) {
				Map map = (Map)obj;
				Map metadata = (Map)map.get("metadata");
				System.out.println("metadata = "+metadata);
				Map labels = (Map)metadata.get("labels");
				System.out.println("labels = "+labels);
				String id = (String)labels.get("stack.appsody.dev/id");
				String ver = (String)labels.get("stack.appsody.dev/version");
				System.out.println("id = "+id+" version = "+ver);
				if (id!=null && ver!=null) {
					System.out.println("id = "+id+" version = "+ver);
					if (id.contentEquals(name) && ver.contentEquals(version)) {
						appNames.add((String)metadata.get("name"));
					}
				}
			}
		} catch (ApiException apie) {
			System.out.println("tolerate: "+apie.getMessage());
			System.out.println("response body: "+apie.getResponseBody());
		}
		
		catch (Exception e) {
			System.out.println("tolerate: "+e.getMessage());
		}
		
		StackApi api = new StackApi(apiClient);

		JSONObject msg = new JSONObject();
		
		String PAT = getPAT();
		if (PAT==null) {
			System.out.println("login token has expired, please login again");
			JSONObject resp = new JSONObject();
			resp.put("message", "your login token has expired or your credentials are invalid, please login again");
			return Response.status(401).entity(resp).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
		}
		
		List curatedStacks = getCuratedStacks(request, PAT);
		Object o = curatedStacks.get(0);
		if (o instanceof JSONObject) {
			JSONObject element = (JSONObject) o;
			String message = (String) element.get("message");
			if (message.contains("HTTP Code 429:")) {
				return Response.status(429).entity(element).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
			} else {
				return Response.status(424).entity(element).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
			}
		}
		
		try {

			Stack kabStack = api.getStack(namespace, name);
			System.out.println("*** reading stack object: "+kabStack);
			if (kabStack==null) {
				System.out.println("*** " + "Stack name: " + name + " 404 not found");
				msg.put("status", "Stack name: " + name + " 404 not found");
				msg.put("message", "Stack name: " + name + " 404 not found");
				return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
			}
			String status="";
			List<StackStatusVersions> kabStatusVersions=null;
			if (version!=null) {
				kabStatusVersions=kabStack.getStatus().getVersions();
				boolean verMatch=false;
				for (StackStatusVersions versionStatus:kabStatusVersions) {
					if (version.contentEquals(versionStatus.getVersion())) {
						status = versionStatus.getStatus();
						verMatch=true;
					}
				}
				if (!verMatch) {
					String msgStr="Stack: "+name+"  does not have version: "+version;
					System.out.println(msgStr);
					msg.put("status", msgStr);
					msg.put("message", msgStr);
					return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
				}
			} else {
				System.out.println("no version number supplied for stack: "+name);
				msg.put("status", "no version number supplied for stack: "+name);
				msg.put("message", "no version number supplied for stack: "+name);
				return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
			}
			
			
			Map m = StackUtils.getKabStackDigest(kabStack,version);
			String gitRepo = "";
			String image = null;
			image = (String) m.get("imageName"); // docker.io/kabanero/nodejs
			String imageDigest="";
			String containerRegistryURL = "";
			if (image!=null) {
				StringTokenizer st = new StringTokenizer(image,"/");
				containerRegistryURL = st.nextToken();
				String crNameSpace = st.nextToken();
				imageDigest = StackUtils.getImageDigestFromRegistry(name, version, namespace, crNameSpace, containerRegistryURL);
			}
			
			String kabDigest = (String) m.get("digest");
			
			System.out.println("curated stacks in describe: "+curatedStacks);
			
			String repoUrl="";
			String repoName = null;
			repoName = StackUtils.getRepoName(curatedStacks, name, version);
			if (repoName!=null) {
				Kabanero k = StackUtils.getKabaneroForNamespace(namespace);
				for (KabaneroSpecStacksRepositories r :  k.getSpec().getStacks().getRepositories()) {
					if (r.getName().contentEquals(repoName)) {
						repoUrl = r.getHttps().getUrl();
					}
				}
			}
			
			if (repoName==null) {
				status = status + " (obsolete)";
			}
			
			msg.put("name", name);
			msg.put("version", version);
			msg.put("git repo url", repoUrl);  
			msg.put("image", image); 
			msg.put("status", status);
			msg.put("digest check", StackUtils.digestCheck(kabDigest, imageDigest, status));
			msg.put("kabanero digest", kabDigest);
			msg.put("image digest", imageDigest);
			msg.put("project", namespace);
			msg.put("applications", appNames.toString());
			return Response.ok(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
		} catch (ApiException apie) {
			apie.printStackTrace();
			String responseBody = apie.getResponseBody();
			System.err.println("Response body: " + responseBody);
			msg.put("status",
					"Stack name: " + name + " version: "+version+" failed to retrieve stack metadat, message: " + apie.getMessage());
			msg.put("message",
					"Stack name: " + name + " version: "+version+" failed to retrieve stack metadat, message: " + apie.getMessage());
			msg.put("exception message", apie.getMessage()+", cause: "+apie.getCause());
			return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.put("status",
					"Stack name: " + name + " version: "+version+" failed to retrieve stack metadat, message: " + e.getMessage());
			msg.put("message",
					"Stack name: " + name + " version: "+version+" failed to retrieve stack metadat, message: " + e.getMessage());
			msg.put("exception message", e.getMessage()+", cause: "+e.getCause());
			return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").header("X-Content-Type-Options","nosniff").build();
		}


	}


	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/stacks/{name}/versions/{version}")
	public Response deActivateStack(@Context final HttpServletRequest request,
			@PathParam("name") final String name, @PathParam("version") final String version) throws Exception {
		// make call to kabanero to delete collection
		
		Kabanero kab = StackUtils.getKabaneroForNamespace(namespace);
		
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
				msg.put("message", "Stack name: " + name + " 404 not found");
				return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
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
					String msgStr="Stack: "+name+"  does not have version: "+version;
					System.out.println(msgStr);
					msg.put("status", msgStr);
					msg.put("message", msgStr);
					return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
				}
			} else {
				System.out.println("no version number supplied for stack: "+name);
				msg.put("status", "no version number supplied for stack: "+name);
				msg.put("message", "no version number supplied for stack: "+name);
				return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
			}
			System.out.println(kabStack.getSpec().getName()+" stack for patch deactivate: " + kabStack.toString());
			kabStack.getSpec().setVersions(kabSpecVersions);
			api.patchStack(namespace, kabStack.getMetadata().getName(), kabStack);
			System.out.println("*** " + "Stack name: " + name + " deactivated");
			msg.put("status", "Stack name: " + name + " version: "+version+" deactivated");
			msg.put("message", "Stack name: " + name + " version: "+version+" deactivated");
			msg.put("repositories", getRepositories(kab));
			return Response.ok(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();
		} catch (ApiException apie) {
			apie.printStackTrace();
			String responseBody = apie.getResponseBody();
			System.err.println("Response body: " + responseBody);
			msg.put("status",
					"Stack name: " + name + " version: "+version+" failed to deactivate, exception message: " + apie.getMessage());
			msg.put("message",
					"Stack name: " + name + " version: "+version+" failed to deactivate, exception message: " + apie.getMessage());
			msg.put("exception message", apie.getMessage()+", cause: "+apie.getCause());
			return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").build();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			msg.put("status",
					"Stack name: " + name + " version: "+version+" failed to deactivate, exception message: " + e.getMessage());
			msg.put("message",
					"Stack name: " + name + " version: "+version+" failed to deactivate, exception message: " + e.getMessage());
			msg.put("exception message", e.getMessage()+", cause: "+e.getCause());
			return Response.status(400).entity(msg).header("Content-Security-Policy", "default-src 'self'").header("X-Content-Type-Options","nosniff").header("X-Content-Type-Options","nosniff").build();
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
