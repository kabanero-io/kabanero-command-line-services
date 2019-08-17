package kabasec;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

public class UserCredentials {

    private String userId;
    private String passwordOrPat;

    public UserCredentials(Properties props) throws KabaneroSecurityException {
        String id = props.getProperty(Constants.LOGIN_KEY_GITHUB_USER);
        String pat = props.getProperty(Constants.LOGIN_KEY_GITHUB_PASSWORD_OR_PAT);
        if (id == null && pat == null) {
            throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "The [" + Constants.LOGIN_KEY_GITHUB_USER + "] and [" + Constants.LOGIN_KEY_GITHUB_PASSWORD_OR_PAT + "] properties are missing from the request.");
        }
        String cumulativeExceptionMsg = "";
        if (id == null || id.isEmpty()) {
            cumulativeExceptionMsg = "The [" + Constants.LOGIN_KEY_GITHUB_USER + "] property is missing or empty.";
        }
        if (pat == null || pat.isEmpty()) {
            if (!cumulativeExceptionMsg.isEmpty()) {
                cumulativeExceptionMsg += " ";
            }
            cumulativeExceptionMsg += "The [" + Constants.LOGIN_KEY_GITHUB_PASSWORD_OR_PAT + "] property is missing or empty.";
        }
        if (!cumulativeExceptionMsg.isEmpty()) {
            throw new IncompleteCredentialsException(cumulativeExceptionMsg);
        }
        this.userId = id;
        this.passwordOrPat = pat;
    }

    String getId() {
        return userId;
    }

    String getPasswordOrPat() {
        return passwordOrPat;
    }

    /**
     * This constructor has been deprecated because we don't plan on relying on extracting user credentials from the
     * Authorization header. Base security and MP-JWT code would typically be invoked if an Authorization header is seen in the
     * request, which could result in distracting FFDCs and error messages in the server logs.
     */
    @Deprecated
    public UserCredentials(HttpHeaders headers) throws KabaneroSecurityException {
        String header = getAuthorizationHeader(headers);
        if (header == null) {
            throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "The value of the Authorization header in the request is null.");
        }
        String user = null;
        String pat = null;
        try {
            String[] parts = getCredentialsFromBasicAuthHeader(header);
            user = parts[0];
            pat = parts[1];
        } catch (Exception e) {
            e.printStackTrace(System.out);
            throw new KabaneroSecurityException("Exception occurred while extracting credentials from the Authorization header: " + e, e);
        }
        this.userId = user;
        this.passwordOrPat = pat;
    }

    @Deprecated
    private String getAuthorizationHeader(HttpHeaders headers) throws KabaneroSecurityException {
        if (headers == null) {
            throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "No HTTP headers were provided.");
        }
        List<String> hvalues = headers.getRequestHeader(HttpHeaders.AUTHORIZATION);
        if (hvalues == null || hvalues.size() == 0) {
            throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "An Authorization header was not present in the request.");
        }
        String header = hvalues.get(0);
        if (header == null || header.isEmpty()) {
            throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "The Authorization header was null or empty.");
        }
        return header;
    }

    @Deprecated
    private String[] getCredentialsFromBasicAuthHeader(String headerValue) throws UnsupportedEncodingException {
        String credentialsString = headerValue.substring("Basic ".length());
        credentialsString = b64dec(credentialsString);
        return credentialsString.split(":");
    }

    @Deprecated
    private String b64dec(String in) throws UnsupportedEncodingException {
        byte[] ba = in.getBytes("UTF-8");
        return new String(Base64.getDecoder().decode(ba));
    }

}
