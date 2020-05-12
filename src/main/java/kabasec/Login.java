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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@PermitAll()
@Path("/login")
public class Login {

    @Context
    HttpServletResponse response;
    
    static long lastTimeJWTissued=0;
    
    static HashMap<String, Object> trackJWTs = new BoundedHashMap(5000);

    /**
     * Uses GitHub credentials from the request body to obtain user information that is used to create and return a JWT that
     * represents the user. The credentials must be specified as request body parameters in JSON format as shown here:
     * 
     * <pre>
     * {"gituser":"myId","gitpat":"myPasswordOrPersonalAccessToken"}
     * </pre>
     * 
     * The <code>"gituser"</code> entry must be the GitHub ID of the user. The <code>"gitpat"</code> entry must be either the
     * password for the user's GitHub account or a personal access token that the user has generated. Note that for GitHub
     * accounts with two-factor authentication (2FA) enabled, a personal access token MUST be used. The personal access token must
     * be generated with at least the <code>read:org</code> scope under <code>admin:org</code>.
     * 
     * @param args
     *            JSON object expected to contain <code>"gituser"</code> and <code>"gitpat"</code> top level keys.
     * 
     * @return JSON object containing the following top level keys:
     *         <ul>
     *         <li><code>"jwt"</code>: The JWT for the authenticated user if the user was successfully authenticated. If
     *         authentication failed, this key will NOT be present.
     *         <li><code>"message"</code>:If an error occurred, this will be a string that describes the error. If authentication
     *         was successful, the value of this entry is undefined.
     *         </ul>
     *         Return codes that can be returned:
     *         <ul>
     *         <li>200: Returned if authentication was successful.
     *         <li>400: Returned for malformed requests or requests missing information. Examples include missing or empty
     *         credentials in the request body or certain errors communicating with GitHub APIs.
     *         <li>401: Returned from GitHub APIs for unauthorized requests.
     *         <li>500: Returned if an internal error occurred that cannot be fixed simply by modifying the request.
     *         </ul>
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Properties login(Properties args) {
        UserCredentials creds = null;
        String msg = "login failed, you may want to check your authorization configuration. Check the CLI Service pod for more information.";
        try {
            creds = new UserCredentials(args);
        } catch (KabaneroSecurityException e) {
            return returnError(e.getStatusCode(), "The login request cannot be processed because an error occurred getting credentials from the request.", e);
        }
        String jwt = null;
        try {
            Authentication auth = new Authentication();
            checkTeamsAndGithubURL(auth);
            jwt = auth.getJwt(creds);  // check id, password/PAT, and team membership here.
        } catch (Exception e) {
        	if (checkApiUrlException(e)) {
        		msg = "login failed, you may want to check your authorization configuration. Double check the apiUrl: in your github: configuraton in the Kabanero CR Instance to make sure it's correct";
        		System.out.println("An error occurred during authentication for user, double check the apiUrl: in your github: configuraton in the Kabanero CR Instance to make sure it's correct, exception message:  "+e.getMessage());
        		Properties p = new Properties();
                p.put("message", msg);
                return p;
        	} else {
        		System.out.println("An error occurred during authentication for user, exception message: "+e.getMessage());
        	}
        	Properties p = new Properties();
            p.put("message", msg);
            return p;
        }
        throttle();
        lastTimeJWTissued=System.currentTimeMillis();
        // logout/invalidate the previous JWT
        if (trackJWTs.get(creds.getId())!=null) {
        	String jwtOld=(String) trackJWTs.get(creds.getId());
        	JwtTracker.add(jwtOld);
        }
        // put/replace id:JWT in the hashtable
        trackJWTs.put(creds.getId(), jwt);
        return returnSuccess(jwt);
    }
    
    private boolean checkApiUrlException(Exception e) {
    	boolean urlException=false;
    	String msg = e.getMessage();
    	e.printStackTrace();
    	System.out.println("msg="+msg+"|||");
    	final String error1="Encountered an error requesting, parsing";
    	final String error2="An error occurred during authentication for user Unexpected char";
    	final String error3="Unexpected char 60";
    	final String error4="could not parse exception response";
    	final String error5="An error occurred during authentication for user, double check the apiUrl:";
    	if (msg.contains(error1)) urlException=true;
    	if (msg.contains(error2)) urlException=true;
    	if (msg.contains(error3)) urlException=true;
    	if (msg.contains(error4)) urlException=true;
    	if (msg.contains(error5)) urlException=true;
    	System.out.println("urlException="+urlException);
    	return urlException;
    }
    
    private void throttle() {
    	// if it's less than 10 seconds, then sleep for 10 seconds
        if (System.currentTimeMillis()-lastTimeJWTissued<=10000) {
        	try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
    }
    
    private void checkTeamsAndGithubURL(Authentication auth) throws KabaneroSecurityException {
        String errmsg = "";
        String preamble = "The Github configuration is not complete: ";
        if(! auth.areGithubTeamsConfigured()) {
            errmsg = "Github teams or organization have not been defined";
        }
        if(! auth.isGithubURLConfigured()) {
            errmsg += " Github API URL has not been defined.";
        }
        if (!"".equals(errmsg)) {
            //539 is agreed upon with cli.
            throw new KabaneroSecurityException(539, preamble + errmsg);
        }
    }

    private Properties returnError(int responseStatus, String errorMsg, Exception e) {
        if (responseStatus == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            return returnInternalError(errorMsg, e);
        }
        Properties p = new Properties();
        p.put("message", buildErrorMessageString(errorMsg, e));
        response.setStatus(responseStatus);
        return p;
    }

    private Properties returnInternalError(String errorMsg, Exception e) {
        System.err.println(buildErrorMessageString(errorMsg, e));
        Properties p = new Properties();
        p.put("message", "An internal error occurred.");
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return p;
    }

    private String buildErrorMessageString(String errorMsg, Exception e) {
        String fullErrorMsg = "An error occurred.";
        if (errorMsg != null && !errorMsg.isEmpty()) {
            fullErrorMsg += " " + errorMsg;
        }
        if (e != null) {
            fullErrorMsg += " " + e.getMessage();
        }
        return fullErrorMsg;
    }

    private Properties returnSuccess(String jwt) {
        Properties p = new Properties();
        p.put("jwt", jwt);
        p.put("message", "ok");
        return p;
    }
    
    static class BoundedHashMap extends LinkedHashMap<String, Object> {

        private static final long serialVersionUID = 7306671418293201026L;

        private int MAX_ENTRIES = 10000;

        public BoundedHashMap(int maxEntries) {
            super();
            if (maxEntries > 0) {
                MAX_ENTRIES = maxEntries;
            }
        }

        public BoundedHashMap(int initSize, int maxEntries) {
            super(initSize);
            if (maxEntries > 0) {
                MAX_ENTRIES = maxEntries;
            }
        }

        public BoundedHashMap() {
            super();
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }

    }

}