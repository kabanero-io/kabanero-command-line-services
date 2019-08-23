package kabasec;

import java.util.ArrayList;
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
        } catch (Exception e) {
            throw new KabaneroSecurityException("An error occurred while building a JWT for user [" + creds.getId() + "]. " + e, e);
        }
        return jwt;
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
            String[] groupsForThisTeam = getGroupNames(teamName);
            for(int j=0; j< groupsForThisTeam.length; j++) {
                if (! groupsList.contains(groupsForThisTeam[j])){
                    groupsList.add(groupsForThisTeam[j]);
                }
            }
        }
        return groupsList;
    }
    
    
 
    /**
     * get groups for a given team using mpConfig. 
     * Groups are defined by variables named groupsForTeam_(teamname) in comma separated format.
     * example: <variable name="groupsForTeam_all-ibmers@IBM" value="operator,admin" />
     * If nothing found and name contains @, try again with @ --> _ so can use unix env vars.
     * @param teamName
     * @return group names if any were found
     */
    private String[] getGroupNames(String teamName){
        String[] result = null;
        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        String groups = null;
        try {
            System.out.println(" search: "+ Constants.ROLESPREFIX + teamName);
            groups = config.getValue(Constants.ROLESPREFIX + teamName, String.class);
            System.out.println(" result: " + groups);
        }catch (NoSuchElementException e) {
            try {
                // mpconfig doesn't convert blanks to _, but we will for convenience.
                String teamName2=teamName.replace(" ", "_");
                System.out.println(" search2: "+ Constants.ROLESPREFIX + teamName2);
                groups = config.getValue(Constants.ROLESPREFIX + teamName2, String.class); 
                System.out.println(" result2: "+ groups);
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