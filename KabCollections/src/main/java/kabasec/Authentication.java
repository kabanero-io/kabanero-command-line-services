package kabasec;

import java.util.ArrayList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletResponse;

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
            String encryptedPat = (new PATHelper()).encrypt(creds.getPasswordOrPat());
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
            builder.claim("groups", convertJsonArrayToList(teams));
        }
        return builder.buildJwt().compact();
    }

    private List<String> convertJsonArrayToList(JsonArray array) {
        List<String> convertedList = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            convertedList.add(array.getString(i));
        }
        return convertedList;
    }

}