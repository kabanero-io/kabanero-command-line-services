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
package kabasec;

import java.security.Principal;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/logout")
public class Logout {

    @Context
    HttpServletRequest request;

    private HttpUtils httpUtils = new HttpUtils();

    /**
     * log out a user, well, as much as we can with a JWT.
     * The call to request.logout will cause the JWT to be placed in
     * a list of logged out JWTs so it cannot be used again. This
     * isn't perfect, the list is in memory only.
     * 
     * @return Properties as follows:
     *         success: true or false
     *         message: undefined if successful, some explanation otherwise.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Properties logout() {
      
        Properties p = new Properties();
        try {
            Principal principal = request.getUserPrincipal();
            if (principal != null && principal.getName() != null) {
                request.logout();
                trackLoggedOutJwt(request);
            }
        } catch (Exception e) {
            p.put("message", "Exception occurred: " + e);
            p.put("success", "false");
            System.out.println("Unexpected exception during logout: " + e);
            e.printStackTrace(System.out);
            return p;
        }
        p.put("success", "true");
        p.put("message", "ok");
        return p;

    }

    private void trackLoggedOutJwt(HttpServletRequest request) {
        String jwt = httpUtils.getBearerTokenFromAuthzHeader(request);
        if (jwt != null) {
            JwtTracker.add(jwt);
        }
    }

}
