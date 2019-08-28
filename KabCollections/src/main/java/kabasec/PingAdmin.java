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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kabasec;

import java.util.Properties;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

// user has to be in admin role to ping this one
@RolesAllowed("admin")  
@Path("/adminping")
public class PingAdmin { 

	@POST	
    @Produces(MediaType.APPLICATION_JSON)	
    public Properties adminPing() {  
		Properties p = new Properties();
		p.put("success","true");
		p.put("message", "pong");
		// uncomment below  to test encrypt / decrypt of PAT
		/*
		String pat = new PATHelper().extractGithubAccessTokenFromSubject() ;
		if (pat == null ) {
		    pat = "null";
		}
		p.put("message", pat);
		*/
		return p;
    }
	
}
