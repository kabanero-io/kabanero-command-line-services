package kabasec;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import javax.annotation.Priority;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.Provider;

import com.ibm.json.java.JSONObject;

@PreMatching
@Priority(value = 10)
@Provider
public class KabSecFilter implements ContainerRequestFilter {

    private HttpUtils httpUtils = new HttpUtils();

    public KabSecFilter() {
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
    	try {
    		String uri = requestContext.getUriInfo().getRequestUri().toString();
    		if (uri.endsWith("/logout") || uri.endsWith("/logout/")) {
    			return;
    		}
    		if (uri.endsWith("/login") || uri.endsWith("/login/")) {
    			if (uri.startsWith("https:")) {
    				System.out.println("uri starts with https:");
    			}
    			System.out.println("uri="+uri);
    		}
    		String jwt = httpUtils.getBearerTokenFromAuthzHeader(requestContext);
    		if (isJwtPreviouslyLoggedOut(jwt)) {
    			ResponseBuilder responseBuilder = Response.serverError();
    			JsonObject responseBody = Json.createObjectBuilder().add("message", "401: The supplied JWT was previously logged out.").build();
    			Response response = responseBuilder.entity(responseBody.toString()).status(401).build();
    			requestContext.abortWith(response);
    		}
    		if (jwt!=null) {
    			if (!isJWTFromThisPod(jwt)) {
    				ResponseBuilder responseBuilder = Response.serverError();
    				System.out.println("The supplied JWT is not from the active pod. JWT="+jwt);
    				JsonObject responseBody = Json.createObjectBuilder().add("message", "401: The supplied JWT is not from the active pod.").build();
    				Response response = responseBuilder.entity(responseBody.toString()).status(401).build();
    				requestContext.abortWith(response);
    			}
    		}
    	} catch (Exception e) {
    		e.printStackTrace();
    		ResponseBuilder responseBuilder = Response.serverError();
    		String msg ="Unexpected exception: "+e.getMessage()+", cause: "+e.getCause()+" occurred";
    		System.out.println(msg);
			JsonObject responseBody = Json.createObjectBuilder().add("message", msg).build();
			Response response = responseBuilder.entity(responseBody.toString()).status(500).build();
			requestContext.abortWith(response);
    	}
        
    }
    
    // Check to see if the JWT on the thread is from this 
    private boolean isJWTFromThisPod(String jwt) {
    	JSONObject jwt_JSON = null;
    	try {
    		String[] parts = jwt.split("\\.");   
    		String decoded = b64dec(parts[1]);
    		jwt_JSON = JSONObject.parse(decoded);
    	} catch (Exception e) {
    		e.printStackTrace();
    		return false;
    	}
    	long podInstanceFromJWT = (long) jwt_JSON.get(Constants.POD_INSTANCE_CLAIM);
    	
    	System.out.println("podInstanceFromJWT="+podInstanceFromJWT);
    	System.out.println("Authentication.podinstance="+Authentication.podinstance);
    	
    	boolean isFromThisPod = (podInstanceFromJWT == Authentication.podinstance);
    	System.out.println("It is "+isFromThisPod+" that this JWT is from this pod");
    	return isFromThisPod;
    }

    private String b64dec(String in) throws UnsupportedEncodingException {
        byte[] ba = in.getBytes("UTF-8");
        return new String(Base64.getDecoder().decode(ba));
    }

    private boolean isJwtPreviouslyLoggedOut(String jwt) {
        if (jwt != null) {
            return JwtTracker.isLoggedOut(jwt);
        }
        return false;
    }
    
   

}
