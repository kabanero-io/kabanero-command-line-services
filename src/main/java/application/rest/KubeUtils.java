package application.rest;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1SecretList;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HttpsURLConnection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;
import com.ibm.json.java.JSONObject;
import com.squareup.okhttp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Kubernetes related Utilities */
public class KubeUtils {
    private static final Logger logger = LoggerFactory.getLogger(KubeUtils.class);

    

    /* Note: DO NOT change the time unit. This is being used in WatchResources.java */
    public static int DEFAULT_READ_TIMEOUT = 60;
    public static TimeUnit DEFAULT_READ_TIMEOUT_UNIT = TimeUnit.SECONDS;
    

    public static int MAX_LABEL_LENGTH= 63; // max length of a label in Kubernetes
	public static int MAX_NAME_LENGTH = 253; // max length of a name in Kubernetes

    /* Caclulate cache key for a resource
       kind: resource kind
       namesapce: resource namespace
       name: resource name
     */
    public static String cacheKey(String kind, String namespace, String name) {
        if (namespace == null || "".equals(namespace)) {
            return kind + "/" + name;
        } else {
            return kind + "/" + namespace + "/" + name;
        }
    }

    /* Calculate cache key for a resrouce */
    public static String cacheKey(ResourceInfo resInfo ) {
        return cacheKey(resInfo.kind, resInfo.namespace, resInfo.name);
    }

    /* Parsed Kubernetes resource */
    public static class ResourceInfo {
         Map resource; // The actual resource
         String kind;
         String name; 
         String namespace;
         String kubeCellName; // name of corresponding WAS-ND-Cell if this is a WAS-Traditional-App, or name of Liberty-Collective if this is a Libert-App
         String version; // version of the resource
    }


    /* Parse JSON representtaion into ResourceInfo
       resource: JSON representation of the resrouce
     */
    public static ResourceInfo getResourceInfo(Map resource ) {
        return getResourceInfo(resource,  null);
    }

    /* Return a ResourceInfo structure containing information 
       about a Kube resource. Return null if not all relevant info can be found
       If kind is specified, use the specified kind when kind is
       not present is the resoruce. This is needed when
       the resource in the Map is not expected to contain kind (for example,
       resources returned from list operation from Kube).
     */
    public static ResourceInfo getResourceInfo(Map resource, String kind) {

        Object kindObj = resource.get("kind");
        if (kindObj == null ) {
             if ( kind == null) 
                 return null;
        } else {
            kind = (String)kindObj;
        }

        Object metadataObj = resource.get("metadata");
        if (metadataObj == null ) {
             return null;
        }
        if ( ! (metadataObj instanceof Map)) {
           return null;
        }
        Map metadata = (Map)metadataObj;

        String kubeCellName = null;
        Object annotationsObj = metadata.get("annotations");
        if ( annotationsObj != null) {
             Map annotations = (Map)annotationsObj;
             Object kubeCellNameObj = annotations.get("prism.platform.name");
             if ( kubeCellNameObj != null) {
                 kubeCellName = (String)kubeCellNameObj;
             }
        }

        Object nameObj = metadata.get("name");
        if ( nameObj == null) {
            return null;
        }
        String name = (String)nameObj;

        String namespace = (String) metadata.get("namespace");


        Object versionObj = metadata.get("resourceVersion");
        if ( versionObj == null) {
            return null;
        }
        String version = (String)versionObj;

        ResourceInfo resInfo = new ResourceInfo();
        resInfo.resource = resource;
        resInfo.kind = kind;
        resInfo.name = name;
        resInfo.namespace = namespace;
        resInfo.kubeCellName = kubeCellName;
        resInfo.version = version;

        return resInfo;
    }

     // get resource version from a resource
    public static String getResourceVersion(Map resource) {
        if (resource == null) return null;


        Object metadataObj = resource.get("metadata");
        if (metadataObj == null ) {
             return null;
        }
        if ( ! (metadataObj instanceof Map)) {
           return null;
        }
        Map metadata = (Map)metadataObj;

        Object versionObj = metadata.get("resourceVersion");
        if ( versionObj == null) {
            return null;
        }
        return (String)versionObj;

    }



    private static void trustAllCerts(ApiClient apiClient) throws Exception {
            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            } };
    
            SSLContext sc = SSLContext.getInstance("TLSv1.2");

            // use the same key manager as kube client
            sc.init(apiClient.getKeyManagers(), trustAllCerts, new SecureRandom());
            // needed for SOAP connector
            SSLContext.setDefault(sc);

            // needed for Kube client
            apiClient.getHttpClient().setSslSocketFactory(sc.getSocketFactory());
            
            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).allEnabledCipherSuites().build();
            apiClient.getHttpClient().setConnectionSpecs(Collections.singletonList((spec)));
    }


    private static ApiClient apiClient;
    public static ApiClient getApiClient() throws Exception {
        synchronized(ApiClient.class) {
            if ( apiClient == null ) {
                apiClient = Config.defaultClient();
                apiClient.getHttpClient().setReadTimeout(DEFAULT_READ_TIMEOUT, DEFAULT_READ_TIMEOUT_UNIT);
                trustAllCerts(apiClient);
                Configuration.setDefaultApiClient(apiClient);
            }
            return apiClient;
        }
    }


    /* @Return true if character is valid for a domain name */
    private static boolean isValidChar(char ch) {
         return (ch == '.' || ch == '-' || 
                   (ch >= 'a' && ch <= 'z') ||
                   (ch >= '0' && ch <= '9'));
    }


    // Convert string to domain name with up to MAX_NAME_LENGTH 
    public static String toDomainName(String name) {
        return toDomainName(name, MAX_NAME_LENGTH);
    }

    /* Convert a name to domain name format.
       The name must 
       - Start with [a-z0-9]. If not, "0" is prepended.
       - lower case. If not, lower case is used.
       - contain only '.', '-', and [a-z0-9]. If not, "." is used insteaad.
       - end with alpha numeric characters
       - can't have consecutive '.'.  Consecutivie ".." is substituted with ".".
    */
    public static String toDomainName(String name, int maxLength) {
         name = name.toLowerCase();
         char [] chars = name.toCharArray();
         StringBuffer ret = new StringBuffer();
         int len = chars.length;
         if ( len > maxLength)
             len = maxLength;
         for (int i=0; i < len; i++) {
             char ch = chars[i];
             if (i == 0 ) {
                 // first character must be [a-z0-9]
                 if ( (ch >= 'a' && ch <= 'z') || 
                     (ch >= '0' && ch <= '9')){
                     ret.append(ch);
                 } else {
                     ret.append('0');
                     if (isValidChar(ch)) {
                          ret.append(ch);
                      } else {
                          ret.append('.');
                      }
                 }
              } else if ( i == len -1) {
                  // laast char must be alphanumeric
                 if ( (ch >= 'a' && ch <= 'z') || 
                     (ch >= '0' && ch <= '9')){
                     ret.append(ch);
                 } else {
                     // last char is not alphanumeric
                     if ( isValidChar(ch) ){
                         // still a valid domain name.
                         if ( i < maxLength -1){
                             // room for 2 more characters
                             ret.append(ch);
                             ret.append('0');
                         } else {
                             ret.append('0');
                         }
                     } else {
                         // not valid 
                         ret.append('0');
                     }
                 }
              } else {
                  if (isValidChar(ch)) {
                      ret.append(ch);
                  } else {
                      ret.append('.');
                  }
               } 
          }

         // change all ".." to ".
         String retStr = ret.toString().replaceAll("\\.\\.", ".");

         if ( retStr.length() == 0 ) {
             // can only happen if there is no alphnumeric in the incoming string
             retStr = "no-alpha-numeric"  ;
         }
         return retStr;
    }

    private static boolean isValidLabelChar(char ch) {
         return (ch == '.' || ch == '-' || (ch == '_') ||
                   (ch >= 'a' && ch <= 'z') ||
		   (ch >= 'A' && ch <= 'Z') ||
                   (ch >= '0' && ch <= '9'));
    }

    /* Convert a name to a label 
       The name must 
       - Start with [a-z0-9A-Z]. If not, "0" is prepended.
       - End with [a-z0-9A-Z]. If not, "0" is appended
       - Intermediate characters can only be: [a-z0-9A-Z] or '_', '-', and '.' If not, '.' is used.
	   - be maximum MAX_LABEL_LENGTH characters long 
    */
    public static String toLabelName(String name) {
         char [] chars = name.toCharArray();
         StringBuffer ret = new StringBuffer();
         int len = chars.length;
         if ( len >= MAX_LABEL_LENGTH)
             len = MAX_LABEL_LENGTH;
         for (int i= 0; i < len; i++) {
             char ch = chars[i];
             if (i == 0 ) {
                 // first character must be [a-z0-9]
                if ( (ch >= 'a' && ch <= 'z') || 
                     (ch >= 'A' && ch <= 'Z') ||
                     (ch >= '0' && ch <= '9')){
                    ret.append(ch);
                } else {
                    ret.append('0');
                    if (isValidLabelChar(ch)) {
                         ret.append(ch);
                     } else {
                         ret.append('.');
                     }
                }
             } else if ( i == len-1) {
                 // last char must be [a-z0-9A-Z]
                 if ( ( (ch >= 'a' && ch <= 'z') || 
                     (ch >= 'A' && ch <= 'Z') ||
                     (ch >= '0' && ch <= '9'))){
                     // last char is valid
                     ret.append(ch);
                 } else { 
                    if ( isValidLabelChar(ch)) {
                        // last char is still a valid label character
                        if ( i < MAX_LABEL_LENGTH -1 ) {
                            // there is space for 2 chars
                            ret.append(ch);
                            ret.append('0');
                        } else {
                            // no space for two characters.
                            // substitute with a valid character
                            ret.append('0');
                        }
                    } else {
                        // last char is not a valid label character
                        ret.append('0');
                    }
                 }
             } else {
                 if (isValidLabelChar(ch)) {
                     ret.append(ch);
                 } else {
                     ret.append('.');
                 }
              }
         }

         return ret.toString();
    }

    /* Delete a resource in Kubernetes
       client: Client to Kubernetes
       namespace: namespace of resource
       name: name of resource
       group: gruop of resource
       version: version of resource
       plural: plural of resource
     */
    public static int deleteKubeResource(ApiClient client, String namespace, String name,
    		String group, String version, String plural) throws Exception {
    	logger.info("Deleteing Resource {}/{}", namespace, name);
    	try {
    		CustomObjectsApi customApi = new CustomObjectsApi(client);
    		V1DeleteOptions body = new V1DeleteOptions();
    		Object resp = customApi.deleteNamespacedCustomObject(group, version, namespace, plural, name, body, null, null, null);
    		System.out.println("response from delete: "+resp);
    	} catch(ApiException ex) {
    		int code = ex.getCode();
    		if ( code == 404) {
    			// OK, object no longer exists
    			return 404;
    		}
    		logger.error("Unable to delete resource", ex);
    		throw ex;
    	} catch(Exception ex) {
    		logger.error("Unable to delete resource", ex);
    		throw ex;
    	}
    	return 0;
    }

    /* Create a resource
      apiClient: client to Kubernetes
      group: gorup of resource
      version; version of resource
      plural: plural of resource
      namespace: namespace of resource
      jsonBody: body of resource
      name: name of resource
     */
    public static void createResource(ApiClient apiClient, String group, String version, String plural, String namespace, JsonObject jsonBody) throws Exception {
       logger.info("Creating resource {}/{}/{} {}/{}:", group, version, plural, namespace);
       CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
       customApi.createNamespacedCustomObject(group, version, namespace, plural, jsonBody, "false");
    }
    
    public static void updateResource(ApiClient apiClient, String group, String version, String plural, String namespace, String name, JsonObject jsonBody) throws Exception {
        logger.info("updating resource {}/{}/{} {}/{}:", group, version, plural, namespace, name);
        CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
        customApi.patchNamespacedCustomObject(group, version, namespace, plural, name, jsonBody);
     }

    /* Set status of resource
       apiClient: client to Kubernetes
       group: group of resource
       version: version of resource
       namespace: namespace of resource
       name: name of resource
       jsonBody: JSON body of the status
     */
    public static void setResourceStatus(ApiClient apiClient, String group, String version, String plural, String namespace, String name, JsonObject jsonBody) throws ApiException {
       logger.info("Setting resource status {}/{}/{}/{}/{}", group, version, plural, namespace, name);
       CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
       customApi.patchNamespacedCustomObjectStatus(group, version, namespace, plural, name, jsonBody);
    }
    
    public static List listResources2(ApiClient apiClient, String group, String version, String plural, String namespace) throws ApiException {
        logger.info("Listing resources {}/{}/{}/{}/{}", group, version, plural, namespace);
        LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) mapResources2(apiClient,group, version, plural, namespace);
        List<Map> list=(List)map.get("items");
        return list;
     }
    
    public static List listResourcesSimple(ApiClient apiClient, String group, String version, String plural, String namespace) throws ApiException {
        logger.info("Listing resources {}/{}/{}/{}/{}", group, version, plural, namespace);
        LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) mapResources(apiClient,group, version, plural, namespace);
        List<Map> list=(List)map.get("items");
        
        return list;
     }
    
    
    
    public static List listResources(ApiClient apiClient, String group, String version, String plural, String namespace) throws ApiException {
        logger.info("Listing resources {}/{}/{}/{}/{}", group, version, plural, namespace);
        LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) mapResources(apiClient,group, version, plural, namespace);
        List<Map> list=(List)map.get("items");
        ArrayList aList = new ArrayList();
        for (Map m:list) {
        	Map metadata = (Map) m.get("metadata");
        	String name = (String) metadata.get("name");
        	Map annotations = (Map) metadata.get("annotations");
        	Map spec = (Map) m.get("spec");
        	String collectionVersion = (String) spec.get("version");
        	HashMap outMap = new HashMap();
        	outMap.put("name",name);
        	outMap.put("version", collectionVersion);
        	aList.add(outMap);
        } 
        return aList;
     }
    
    public static Map mapResources2(ApiClient apiClient, String group, String version, String plural, String namespace) throws ApiException {
        logger.info("Listing resources {}/{}/{}/{}/{}", group, version, plural, namespace);
        System.out.println("entering mapResources2");
        CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
        Object obj = customApi.listClusterCustomObject(group, version, plural, "true", "","", 60, false);
        LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) obj;
        return map;
     }
    
    public static Map mapResources(ApiClient apiClient, String group, String version, String plural, String namespace) throws ApiException {
        logger.info("Listing resources {}/{}/{}/{}/{}", group, version, plural, namespace);
        CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
        Object obj = customApi.listNamespacedCustomObject(group, version, namespace, plural, "true", "", "", 60, false);
        System.out.println("current kab collections="+obj.toString());
        LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) obj;
        return map;
     }
    
    public static Map mapOneResource(ApiClient apiClient, String group, String version, String plural, String namespace, String name) throws ApiException {
        logger.info("Listing resources {}/{}/{}/{}/{}", group, version, plural, namespace);
        CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
        Object obj = customApi.getNamespacedCustomObject(group, version, namespace, plural, name);
        System.out.println("current kab collections="+obj.toString());
        LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) obj;
        return map;
     }
    

    public static String listRouteUrl(Map map) {
        String host = "";
        List<Map> list=(List)map.get("items");
        for (Map m:list) {
            Map spec = (Map) m.get("spec");
            host = (String) spec.get("host");
            break;
        }
        return host;
    }
    
	private static String locateCorrectSecret(V1Secret v1secret, String gitURL) throws IOException {
		V1Secret secret = null;
		Iterator it = v1secret.getMetadata().getAnnotations().values().iterator();
		String url = (String) it.next();
		url = url.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)", "");
		String password = null;
		if (url.contentEquals(gitURL)) {
			String annotationStr = (String) it.next();
			JSONObject jo = JSONObject.parse(annotationStr);
			JSONObject stringData = (JSONObject) jo.get("stringData");
			password = (String) stringData.get("password");
		}
		return password;
	}
     
    public static String getSecret(String namespace, String secret_url) throws ApiException {
    	secret_url = secret_url.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)", "");
        String password = null;
        System.out.println("Entering getSecret("+namespace+")");
        try {
            ApiClient apiClient = getApiClient();
            CoreV1Api coreAPI = new CoreV1Api();
            V1SecretList v1secrets = coreAPI.listNamespacedSecret(namespace, false, null, null, null, null, null, null, 30, null);
            List<V1Secret> v1secretList = v1secrets.getItems();
            for (V1Secret v1Secret:v1secretList) {
            	password=locateCorrectSecret(v1Secret, secret_url);
            	if (password!=null) {
            		break;
            	}
            }
            
          } catch (Exception e) {
        	e.printStackTrace();
            System.out.println("exception cause: " + e.getCause());
            System.out.println("exception message: " + e.getMessage());
            throw new ApiException("Error retrieving kubernetes secret for GHE processing, error message: "+e.getMessage()+", cause: "+e.getCause());
        } 
        return password;
     }
    
    private static Map<String,String> locateCorrectSecretUserAndPass(V1Secret v1secret, String gitURL) throws IOException {
		V1Secret secret = null;
		Iterator it = null;
		try {
			it = v1secret.getMetadata().getAnnotations().values().iterator();
		} catch (NullPointerException npe) {
			return null;
		}
		String url = "";
		boolean match = false;
		for (;it.hasNext();) {
			url = (String) it.next();
			if (url!=null) {
				if (url.contains("tekton.dev/docker-0:")) {
					match = false;
					break;
				}
			}
		}
		if (!match) {
			return null;
		}
		
		url = url.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)", "");
		String user=null, password = null;
		if (url.contentEquals(gitURL)) {
			String annotationStr = (String) it.next();
			JSONObject jo = JSONObject.parse(annotationStr);
			JSONObject stringData = (JSONObject) jo.get("stringData");
			user = (String) stringData.get("username");
			password = (String) stringData.get("password");
		}
		HashMap<String,String> m = null;
		if (user!=null && password!=null) {
			m = new HashMap<String,String>();
			m.put("user", user);
			m.put("password", password);
		}
		return m;
	}
    
    public static Map<String,String> getUserAndPasswordFromSecret(String namespace, String secret_url) throws ApiException {
    	secret_url = secret_url.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)", "");
        String password = null;
        System.out.println("Entering getSecret("+namespace+")");
        Map<String,String> m = null;
        try {
            ApiClient apiClient = getApiClient();
            CoreV1Api coreAPI = new CoreV1Api();
            V1SecretList v1secrets = coreAPI.listNamespacedSecret(namespace, false, null, null, null, null, null, null, 30, null);
            List<V1Secret> v1secretList = v1secrets.getItems();
            for (V1Secret v1Secret:v1secretList) {
            	m=locateCorrectSecretUserAndPass(v1Secret, secret_url);
            	if (m!=null) {
            		break;
            	}
            }
            
          } catch (Exception e) {
        	e.printStackTrace();
            System.out.println("exception cause: " + e.getCause());
            System.out.println("exception message: " + e.getMessage());
            throw new ApiException("Error retrieving kubernetes secret for GHE processing, error message: "+e.getMessage()+", cause: "+e.getCause());
        } 
        if (m==null ) {
        	throw new ApiException("Could not retrieve kubernetes secret for GHE or Container Registry processing, try recycling your CLI pod if you created the secret");
        }
        return m;
     }

    public static String getTektonDashboardURL() {
        String route = "";
        try {
            ApiClient apiClient = getApiClient();
            String group = "route.openshift.io";
            String version = "v1";
            String plural = "routes";
            String namespace = "tekton-pipelines";
            List resources = listResourcesSimple(apiClient, group, version, plural, namespace);
            String dashboardUrl = "notfound";
            for (Object obj: resources) {
            	Map m = (Map)obj;
            	Map spec = (Map) m.get("spec");
            	String host = (String) spec.get("host");
            	if (host!=null) {
            		if (host.contains("tekton-dashboard")) {
            			dashboardUrl = host;
            			break;
            		}
            	}
            }
            route += "https://";
            route += dashboardUrl;
        } catch (Exception e) {
            System.out.println("exception cause: " + e.getCause());
            System.out.println("exception message: " + e.getMessage());
        } 
        return route;
     }
}
