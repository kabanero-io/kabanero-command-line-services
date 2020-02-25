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
    		String jwt = httpUtils.getBearerTokenFromAuthzHeader(requestContext);
    		System.out.println("In KabSecFilter, jwt="+jwt);
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
    	}
        
    }
    
    private boolean isJWTFromThisPod(String jwt) {
    	JSONObject jwt_JSON = null;
    	System.out.println("In isJWTFromThisPod, jwt="+jwt);
    	if (jwt!=null) {
    		try {
    			String[] parts = jwt.split("\\.");   
    			String decoded = b64dec(parts[1]);
    			jwt_JSON = JSONObject.parse(decoded);
    		} catch (Exception e) {
    			e.printStackTrace();
    			return false;
    		}

    		String podInstanceFromJWT = (String) jwt_JSON.get(Constants.POD_INSTANCE_CLAIM);
    		long podInstanceFromJWTLong = Long.valueOf(podInstanceFromJWT);

    		boolean isFromThisPod = (podInstanceFromJWTLong == Authentication.podinstance);
    		System.out.println("It is "+isFromThisPod+" that this JWT is from this pod");

    		return isFromThisPod;

    	} else {
    		return true;
    	}
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
