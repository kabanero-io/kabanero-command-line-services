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

import java.io.StringReader;
import java.util.Iterator;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

public class GitHubService {

    private String userId = null;
    private String passwordOrPat = null;

    private HttpUtils httpUtils = new HttpUtils();
    private Config config = new Config();

    public GitHubService(String id, String passwordOrPat) throws KabaneroSecurityException {
        this.userId = id;
        this.passwordOrPat = passwordOrPat;
    }

    /**
     * Obtains the user ID and team information for the GitHub user.
     * 
     * @return Returns a JSON object with the following top level keys:
     *         <ul>
     *         <li><code>"id"</code>: The GitHub ID of the user
     *         <li><code>"teams"</code>: A JSON array of GitHub teams that the user is a member of. Each entry in the array is a
     *         string that takes the format <code>&lt;team-name&gt;@&lt;organization-name&gt;</code>. These team strings are
     *         expected to be mapped to security roles.
     *         </ul>
     */
    JsonObject getUserData() throws KabaneroSecurityException {
        try {
            JsonObjectBuilder jsonResponse = Json.createObjectBuilder();
            jsonResponse.add("id", getUserId());
            jsonResponse.add("teams", getUserTeams());
            return jsonResponse.build();
        } catch (KabaneroSecurityException e) {
            throw new KabaneroSecurityException("Encountered an error requesting, parsing, or processing GitHub data for user [" + userId + "]: " + e.getMessage(), e);
        }
    }

    private String getUserId() throws KabaneroSecurityException {
        try {
        	System.out.println("Gathering github authorization configuration information");
            JsonObject userInfo = getJsonObjectFromApi("GET", config.getUserInfoUrl());
            return userInfo.getString("login");
        } catch (KabaneroSecurityException e) {
            throw new KabaneroSecurityException("Failed to obtain or process user info for user [" + userId + "]. " + e.getMessage(), e);
        }
    }

    /**
     * Returns an ordered array of email addresses associated with the account corresponding to the provided personal access
     * token. The first entry in the array will be the primary email address, if one has been configured. If a primary email
     * address has not been configured in the user's GitHub account, this returns an array of email addresses in whatever
     * order the GitHub API returns them in.
     */
    private JsonArray getUserEmails() throws KabaneroSecurityException {
        try {
            JsonArray emailInfo = getJsonArrayFromApi("GET", config.getEmailUrl());
            JsonArrayBuilder responseBuilder = Json.createArrayBuilder();
            Iterator<JsonValue> iter = emailInfo.iterator();
            while (iter.hasNext()) {
                addEmailEntryToResponse(responseBuilder, iter.next());
            }
            return responseBuilder.build();
        } catch (Exception e) {
            throw new KabaneroSecurityException("Failed to obtain or process emails for user [" + userId + "]. " + e.getMessage(), e);
        }
    }

    private JsonObject getJsonObjectFromApi(String requestMethod, String apiUrl) throws KabaneroSecurityException {
        String gitHubApiResponse = httpUtils.callApi(requestMethod, apiUrl, userId, passwordOrPat);
        return Json.createReader(new StringReader(gitHubApiResponse)).readObject();
    }

    private JsonArray getJsonArrayFromApi(String requestMethod, String apiUrl) throws KabaneroSecurityException {
        String gitHubApiResponse = httpUtils.callApi(requestMethod, apiUrl+"?per_page=100", userId, passwordOrPat);
        return Json.createReader(new StringReader(gitHubApiResponse)).readArray();
    }

    private void addEmailEntryToResponse(JsonArrayBuilder responseBuilder, JsonValue rawValue) {
        JsonObject entry = rawValue.asJsonObject();
        String email = entry.getString("email");
        if (isPrimaryEmail(entry)) {
            responseBuilder.add(0, email);
        } else {
            responseBuilder.add(email);
        }
    }

    private boolean isPrimaryEmail(JsonObject entry) {
        return entry.containsKey("primary") && entry.getBoolean("primary");
    }

    private JsonArray getUserTeams() throws KabaneroSecurityException {
        try {
            JsonArray teamInfo = getJsonArrayFromApi("GET", config.getTeamsUrl());
            JsonArrayBuilder responseBuilder = Json.createArrayBuilder();
            Iterator<JsonValue> iter = teamInfo.iterator();
            while (iter.hasNext()) {
                addTeamEntryToResponse(responseBuilder, iter.next());
            }
            return responseBuilder.build();
        } catch (Exception e) {
            throw new KabaneroSecurityException("Failed to obtain or process teams for user [" + userId + "]. " + e.getMessage(), e);
        }
    }

    private void addTeamEntryToResponse(JsonArrayBuilder responseBuilder, JsonValue rawValue) {
        JsonObject entry = rawValue.asJsonObject();
        String teamName = entry.getString("name");
        String orgName = getOrganizationName(entry);
        String orgAndTeamEntry = createTeamAndOrgNameString(teamName, orgName);
        responseBuilder.add(orgAndTeamEntry);
    }

    private String getOrganizationName(JsonObject entry) {
        JsonObject organizationInfo = entry.getJsonObject("organization");
        // The "login" entry contains the string that's used in URLs and generally seems to be a usable, human-readable string
        return organizationInfo.getString("login");
    }

    private String createTeamAndOrgNameString(String teamName, String orgName) {
        return teamName + "@" + orgName;
    }

}