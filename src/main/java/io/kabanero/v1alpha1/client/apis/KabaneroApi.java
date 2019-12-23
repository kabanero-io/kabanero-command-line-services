package io.kabanero.v1alpha1.client.apis;

import com.google.gson.reflect.TypeToken;

import io.kabanero.v1alpha1.models.Kabanero;
import io.kabanero.v1alpha1.models.KabaneroList;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.ApiResponse;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;
import io.kubernetes.client.models.V1Status;

import java.lang.reflect.Type;

/**
 * Methods to be used to manipulate "kind: Kabanero" objects from a Kubernetes cluster. 
 */
public class KabaneroApi {

	/** The Kubernetes group for Kabanero */
	public static final String GROUP = "kabanero.io";
	
	/** The Kubernetes version for Kabanero */
	public static final String VERSION = "v1alpha1";
	
	/** The plural form of the "kind: Kabanero" */
	public static final String PLURAL = "kabaneros";
	
	/** The Kind that Kubernetes uses to recognize this object. */
	public static final String KABANERO_KIND = "Kabanero";
	
	
    private ApiClient apiClient;

    /**
     * Constructor that uses the default Kubernetes client.
     */
    public KabaneroApi() {
    	this(Configuration.getDefaultApiClient());
    }

    /**
     * Constructor that uses a specific Kubernetes client.
     * @param apiClient The Kubernetes java client.
     */
    public KabaneroApi(ApiClient apiClient) {
    	this.apiClient = apiClient;
    }

    /**
     * Retrieves the Kubernetes java client instance that this api will use.
     * @return The Kubernetes java client instance.
     */
    public ApiClient getApiClient() {
    	return apiClient;
    }

    /**
     * Sets the Kubernetes java client instance that this api will use.
     * @param apiClient The Kubernetes java client instance.
     */
    public void setApiClient(ApiClient apiClient) {
    	this.apiClient = apiClient;
    }

    /**
     * Creates an instance of "kind: Kabanero".
     * @param namespace The namespace to create the object in.
     * @param instance The object instance.
     * @return The object instance as returned by the Kubernetes API server in response to the create call.
     * @throws ApiException Thrown if an error occurred while communicating with the Kubernetes API server.
     */
    public Kabanero createKabanero(String namespace, Kabanero instance) throws ApiException {
    	if (namespace == null) {
    		throw new IllegalArgumentException("namespace must not be null");
    	}
    	
    	if (instance == null) {
    		throw new IllegalArgumentException("instance must not be null");
    	}
    	
    	instance.apiVersion(GROUP + "/" + VERSION).kind(KABANERO_KIND);
    	
        CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
        com.squareup.okhttp.Call call = customApi.createNamespacedCustomObjectCall(GROUP, VERSION, namespace, PLURAL, instance, "false", null, null);
    	Type localVarReturnType = new TypeToken<Kabanero>(){}.getType();
    	ApiResponse<Kabanero> resp = apiClient.execute(call, localVarReturnType);
    	return resp.getData();
    }

    /**
     * Retrieves an instance of "kind: Kabanero".
     * @param namespace The namespace to create the object in.
     * @param name The name of the object instance to retrieve.
     * @return The object instance as returned by the Kubernetes API server in response to the get call.
     * @throws ApiException Thrown if an error occurred while communicating with the Kubernetes API server.
     */
    public Kabanero getKabanero(String namespace, String name) throws ApiException {
    	if (namespace == null) {
    		throw new IllegalArgumentException("namespace must not be null");
    	}
    	
    	if (name == null) {
    		throw new IllegalArgumentException("name must not be null");
    	}
    	
        CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
    	com.squareup.okhttp.Call call = customApi.getNamespacedCustomObjectCall(GROUP, VERSION, namespace, PLURAL, name, null, null);
    	Type localVarReturnType = new TypeToken<Kabanero>(){}.getType();
    	ApiResponse<Kabanero> resp = apiClient.execute(call, localVarReturnType);
    	return resp.getData();
    }
    
    /**
     * Deletes an instance of "kind: Kabanero".
     * @param namespace The namespace to delete the object from
     * @param name The name of the object to delete
     * @param body The delete options as specified by the Kubernetes java client.
     * @return The status of the delete as returned by the Kubernetes API server.
     * @throws ApiException Thrown if an error occurred while communicating with the Kubernetes API server.
     */
    public V1Status deleteKabanero(String namespace, String name, V1DeleteOptions body, Integer gracePeriodSeconds, Boolean orphanDependents, String propagationPolicy) throws ApiException {
    	if (namespace == null) {
    		throw new IllegalArgumentException("namespace must not be null");
    	}
    	if (name == null) {
    		throw new IllegalArgumentException("name must not be null");
    	}
    	V1DeleteOptions deleteOptions = (body == null) ? new V1DeleteOptions() : body;
    	
    	CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
    	com.squareup.okhttp.Call call = customApi.deleteNamespacedCustomObjectCall(GROUP, VERSION, namespace, PLURAL, name, deleteOptions, gracePeriodSeconds, orphanDependents, propagationPolicy, null, null);
    	Type localVarReturnType = new TypeToken<V1Status>() {}.getType();
    	ApiResponse<V1Status> resp = apiClient.execute(call, localVarReturnType);
    	return resp.getData();
    }
    
    /**
     * Retrieves a list of "kind: Kabanero" objects.
     * @param namespace The namespace where the instances should be read from.
     * @param labelSelector An optional label selector to pass to the Kubernetes API server.
     * @return A list of "kind: Kabanero" objects.
     * @throws ApiException Thrown if an error occurred while communicating with the Kubernetes API server.
     */
    public KabaneroList listKabaneros(String namespace, String labelSelector, String resourceVersion, Integer timeoutSeconds) throws ApiException {
    	if (namespace == null) {
    		throw new IllegalArgumentException("namespace must not be null");
    	}
    	
    	CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
        com.squareup.okhttp.Call call = customApi.listNamespacedCustomObjectCall(GROUP, VERSION, namespace, PLURAL, "false", labelSelector, resourceVersion, timeoutSeconds, false, null, null);
    	Type localVarReturnType = new TypeToken<KabaneroList>(){}.getType();
        ApiResponse<KabaneroList> resp = apiClient.execute(call, localVarReturnType);
        return resp.getData();
    }
    
    /**
     * Updates an instance of "kind: Kabanero".
     * @param namespace The namespace to update the object in.
     * @param name The name of the object to update.
     * @param instance The object instance to replace with.
     * @return The object instance as returned by the Kubernetes API server in response to the update call.
     * @throws ApiException Thrown if an error occurred while communicating with the Kubernetes API server.
     */
    public Kabanero updateKabanero(String namespace, String name, Kabanero instance) throws ApiException {
    	if (namespace == null) {
    		throw new IllegalArgumentException("namespace must not be null");
    	}
    	if (name == null) {
    		throw new IllegalArgumentException("name must not be null");
    	}
    	if (instance == null) {
    		throw new IllegalArgumentException("instance must not be null");
    	}
    	
    	instance.apiVersion(GROUP + "/" + VERSION).kind(KABANERO_KIND);

    	CustomObjectsApi customApi = new CustomObjectsApi(apiClient);
        com.squareup.okhttp.Call call = customApi.replaceNamespacedCustomObjectCall(GROUP, VERSION, namespace, PLURAL, name, instance, null, null);
    	Type localVarReturnType = new TypeToken<Kabanero>(){}.getType();
    	ApiResponse<Kabanero> resp = apiClient.execute(call, localVarReturnType);
    	return resp.getData();
    }
    
    /**
     * Creates an OwnerReference that can be used in another object's metadata.
     */
    public V1OwnerReference createOwnerReference(Kabanero k) {
    	V1ObjectMeta metadata = k.getMetadata();
    	return new V1OwnerReference().apiVersion(GROUP + "/" + VERSION).kind(KABANERO_KIND).uid(metadata.getUid()).name(metadata.getName()).controller(true);
    }
}