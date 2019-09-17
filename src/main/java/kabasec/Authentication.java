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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.microprofile.config.ConfigProvider;

import com.ibm.websphere.security.jwt.Claims;
import com.ibm.websphere.security.jwt.JwtBuilder;

public class Authentication {

    private final String JWT_BUILDER_ID = "kabsecbuilder";
    private HashSet<String> allKnownTeamNames = new HashSet<String>();

    /**
     * Build a JWT for the Github user credentials supplied.
     * The JWT will have of the user's Github id as the subject.
     * It will also contain the user's PAT or password in AES-encoded form, that can be 
     * extracted for later use by PATHelper.
     * 
     * Groups in the JWT will contain: 
     * allusers - for any authenticated user
     * all fully qualified Github team names the user is a member of
     * arbitrary group names mapped from environment variables defined as
     * teamsInGroup_groupname=(list of fully qualified Github team names).
     * Initially only an admin group is defined, additional groups can
     * be defined by simply setting additional environment variables. 
     * 
     * Example env. var. for admin group: teamsInGroup_admin="all-ibmers@IBM,Security SSO@OpenLiberty"
     * 
     * Any group name in the JWT can be used in the rolesallowed annotation to 
     * secure other REST operations. 
     * 
     * @param creds - github user id and pat or password
     * @return a jwt if one can be successfully constructed
     * 
     * @throws KabaneroSecurityException if creds aren't valid, 
     * account requires PAT but password was supplied, user is not a member of any team, or any other error
     */
    String getJwt(UserCredentials creds) throws KabaneroSecurityException {
        JsonObject userData = getUserDataFromGitHub(creds.getId(), creds.getPasswordOrPat());
        String id = userData.getString("id");
        JsonArray teams = userData.getJsonArray("teams");        
        if (!id.equals(creds.getId())) {
            throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "Cannot create a JWT because the user ID [" + creds.getId() + "] provided in the request does not match the user ID [" + id + "] that is associated with the access token provided in the request.");
        }        
        String jwt = null;
        try {
            String encryptedPat = new PATHelper().encrypt(creds.getPasswordOrPat());
            jwt = buildJwt(id, encryptedPat, teams);
            // now that buildJwt has probed the environment for all known teams and groups, we can check if user is member of any known teams
            throwExceptionIfUserIsNotInAnyDefinedTeams(creds.getId(), teams);
        } catch (Exception e) {
            String errorMsg = "Error while building a JWT for user [" + creds.getId() + "]. " + e;
            System.out.println(errorMsg);
            e.printStackTrace(System.out);
            throw new KabaneroSecurityException(errorMsg, e);
        }
        return jwt;
    }
    
    private void throwExceptionIfUserIsNotInAnyDefinedTeams(String userId, JsonArray teams)throws KabaneroSecurityException {
        for (int i = 0; i < teams.size(); i++) {
            String teamName =  teams.getString(i); 
            boolean found = allKnownTeamNames.contains(teamName);
            if (!found) {
                found = allKnownTeamNames.contains(convertToOldFormat(teamName));
            }
            if (found) {
                return;
            }
        }  
        String msg = "The user is not a member of any defined teams.";
        System.out.println("Login failed. User " +userId + " was not a member of any defined teams: "+ allKnownTeamNames.toString());
        throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, msg);
            
    }
    
    // replace any nonalphanumeric chars with _
    private String convertToOldFormat(String in) {
        StringBuffer sb = new StringBuffer();
        for(int i=0; i< in.length(); i++) {
            String c = in.substring(i,i+1);
            if ( Constants.ENVIRONMENT_VARIABLE_NAME_ALLOWED_CHARS.contains(c.toUpperCase())) {
                sb.append(c);
            } else {
                sb.append("_");
            }
        }
        return sb.toString();
    }

    private JsonObject getUserDataFromGitHub(String loginId, String pat) throws KabaneroSecurityException {
        GitHubService githubService = new GitHubService(loginId, pat);
        return githubService.getUserData();
    }

    private String buildJwt(String userName, String pat, JsonArray teams) throws Exception {
        JwtBuilder builder = JwtBuilder.create(JWT_BUILDER_ID)
                .jwtId(true)
                .claim(Claims.SUBJECT, userName)
                .claim("upn", userName)
                .claim(Constants.PAT_JWT_CLAIM, pat);
        
        if (teams != null && !teams.isEmpty()) {
            List<String> groupsList = convertJsonArrayToList(teams);
            groupsList.add("allusers"); // default group for everyone
            groupsList = addGroupNamesForTeamsFromEnvironment(teams, groupsList); 
            builder.claim("groups", groupsList);
        }
        return builder.buildJwt().compact();
    }

    private List<String> convertJsonArrayToList(JsonArray array) {
        List<String> convertedList = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            // add explicit team name
            String teamName = array.getString(i);
            convertedList.add(teamName);
        }
        return convertedList;
    }
    
    
    private List<String> addGroupNamesForTeamsFromEnvironment(JsonArray array,  List<String> groupsList) {
        for (int i = 0; i < array.size(); i++) {
            String teamName = array.getString(i); 
            // add group names for team, if defined
            String[] groupsForThisTeam = getGroupNamesOldWay(teamName);  // leave in place for compatibility
            for(int j=0; j< groupsForThisTeam.length; j++) {
                if (! groupsList.contains(groupsForThisTeam[j])){
                    groupsList.add(groupsForThisTeam[j]);
                }
            }
            String[] groupsForThisTeam2 = getGroupNamesNewWay(teamName);  
            for(int j=0; j< groupsForThisTeam2.length; j++) {
                if (! groupsList.contains(groupsForThisTeam2[j])){
                    groupsList.add(groupsForThisTeam2[j]);
                }
            }
        }
        return groupsList;
    }
    
    /**
     * get groups for a team using mpConfig
     * Groups are defined by environment variables named 
     * teamsInGroup_(groupname) where the value of the variable is a comma
     * separated list of fully qualified team names.
     * 
     * example for admin group: teamsInGroup_admin="all-ibmers@IBM,Security SSO@OpenLiberty"
     * 
     * @param teamName
     * @return group names if any were found
     */
    private String[] getGroupNamesNewWay(String teamName) {
        String[] result = new String[] {};
        ArrayList<String> groups = new ArrayList<String>();
        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        
        Iterable<String> props = config.getPropertyNames();
        Iterator<String> it = props.iterator();
        while(it.hasNext()) {
            String prop = it.next();
            if (prop.startsWith(Constants.ROLESPREFIX)) {
                String groupName = prop.substring(Constants.ROLESPREFIX.length());
                if (groupName.length() == 0) {
                    continue;
                }
                String value = config.getValue(prop, String.class);
                System.out.println("getGroupNamesNewWay Team: " + teamName + " Environment Variable: "+ prop + " Value: " + value);
                String[] values = value.split(",");
                for(int i=0; i< values.length; i++) {
                    if (values[i].equals(teamName)) {
                        allKnownTeamNames.add(values[i]);
                        System.out.println("allKnownTeamNames add: "+ values[i]);
                        System.out.println("getGroupNamesNewWay **Group "+ groupName + " added for team: " + teamName);
                        groups.add(groupName);
                    }
                }                
            }            
        }
        System.out.println("getGroupNamesNewWay **groups for team: "+ teamName + ": "+ groups);
        return groups.toArray(new String[] {});
    }
 
    /**
     * get groups for a given team using mpConfig. 
     * Groups are defined by variables named groupsForTeam_(teamname) in comma separated format.
     * example: <variable name="groupsForTeam_all-ibmers@IBM" value="operator,admin" />
     * If nothing found and name contains space, try again with space --> _ so can use unix env vars.
     * 
     * This was found to be cumbersome because team names must have all non-alphanumeric
     * characters replaced with underscores when specifying the environment variable name.
     * The new method places the team name in a value instead of a name, so avoids this. 
     * 
     * @param teamName
     * @return group names if any were found
     */
    @Deprecated
    private String[] getGroupNamesOldWay(String teamName){
        String[] result = null;
        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        String groups = null;
        try {
            System.out.println("getGroupNamesOldWay search: "+ Constants.ROLESPREFIXOLD + teamName);
            groups = config.getValue(Constants.ROLESPREFIXOLD + teamName, String.class);
            System.out.println("getGroupNamesOldWay result: " + groups);
            allKnownTeamNames.add(teamName);
        }catch (NoSuchElementException e) {
            try {
                // mpconfig doesn't convert blanks to _, but we will for convenience.
                String teamName2=teamName.replace(" ", "_");
                System.out.println("getGroupNamesOldWay search2: "+ Constants.ROLESPREFIXOLD + teamName2);
                groups = config.getValue(Constants.ROLESPREFIXOLD + teamName2, String.class); 
                System.out.println("getGroupNamesOldWay result2: "+ groups);
                allKnownTeamNames.add(teamName2);
            } catch (NoSuchElementException e2) {
                // not there
            }
        }
        if (groups != null && groups.length() >0) {
            groups.replace(" ", "");  // get rid of any spaces
            result = groups.split(",");
        } else {
            result = new String[] {};
        }
        return result;
    }
}