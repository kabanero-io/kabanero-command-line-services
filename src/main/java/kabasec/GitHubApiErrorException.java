package kabasec;

import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonObject;

public class GitHubApiErrorException extends KabaneroSecurityException {

    private static final long serialVersionUID = 1L;

    public GitHubApiErrorException(int statusCode, String githubResponse, String message) {
        super(statusCode, createMessageFromGitHubResponse(githubResponse, message));
    }

    private static String createMessageFromGitHubResponse(String githubResponse, String message) {
        String newMessage = message;
        try {
            JsonObject responseJson = Json.createReader(new StringReader(githubResponse)).readObject();
            String ghMessage = responseJson.getString("message");
            newMessage += " The response message from GitHub was \"" + ghMessage + "\".";
            String documentationUrl = responseJson.getString("documentation_url");
            if (documentationUrl != null && !documentationUrl.isEmpty()) {
                newMessage += " See the documentation for this API at " + documentationUrl + ".";
            }
        } catch (Exception e) {
            // Expected the response to be a JSON object but failed to parse or process the string
            newMessage = "Caught exception extracting an error message from the GitHub response [" + githubResponse + "]. Exception was: " + e;
            e.printStackTrace();
            throw new RuntimeException("could not parse exception response");
        }
        return newMessage;
    }

}
