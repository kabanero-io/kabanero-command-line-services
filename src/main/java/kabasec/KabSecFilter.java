package kabasec;

import javax.annotation.Priority;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.Provider;

@PreMatching
@Priority(value = 10)
@Provider
public class KabSecFilter implements ContainerRequestFilter {

    private HttpUtils httpUtils = new HttpUtils();

    public KabSecFilter() {
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {

        String uri = requestContext.getUriInfo().getRequestUri().toString();
        if (uri.endsWith("/logout") || uri.endsWith("/logout/")) {
            return;
        }
        if (isJwtPreviouslyLoggedOut(requestContext)) {
            ResponseBuilder responseBuilder = Response.serverError();
            JsonObject responseBody = Json.createObjectBuilder().add("message", "401: The supplied JWT was previously logged out.").build();
            Response response = responseBuilder.entity(responseBody.toString()).status(401).build();
            requestContext.abortWith(response);
        }
    }

    private boolean isJwtPreviouslyLoggedOut(ContainerRequestContext context) {
        String jwt = httpUtils.getBearerTokenFromAuthzHeader(context);
        if (jwt != null) {
            return JwtTracker.isLoggedOut(jwt);
        }
        return false;
    }

}
