package kabasec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.container.ContainerRequestContext;

import com.ibm.websphere.ssl.JSSEHelper;

public class HttpUtils {
    
    public static boolean accessGitSuccess = true;

    public String callApi(String requestMethod, String apiUrl, String userName, String passwordOrPat) throws KabaneroSecurityException {
        String response = null;
        boolean retry = false;
        int i = 0;
        accessGitSuccess = true;
        while( i++ < 2 ) {
            try {
                HttpURLConnection connection = createConnection(requestMethod, apiUrl, userName, passwordOrPat);
                response = readConnectionResponse(connection);
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpServletResponse.SC_NOT_FOUND) {
                    // Wraps 404s returned from GitHub as 400s instead. See https://developer.github.com/v3/#authentication for why GitHub returns 404s in some cases.
                    throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "An error occurred contacting GitHub API [" + apiUrl + "]. Verify that your personal access token was generated with at least the minimum required scopes.");
                }
                if (responseCode != 200) {
                    throw new GitHubApiErrorException(responseCode, response, "Received unexpected " + responseCode + " response from " + requestMethod + " request sent to " + apiUrl + ".");
                }                
                break;  //success
            } catch (IOException e) { 
                if(retry) {  // already retried once, give up.
                    accessGitSuccess = false;
                    throw new KabaneroSecurityException("Connection to GitHub API [" + apiUrl + "] failed. Ensure SSL settings are correct and that the SSL certificate for the API is included in the truststore configured for the server. " + e, e);
                }
                System.out.println("Received IO Exception [ "+ e + " ] during communication with GitHub API [" + apiUrl + "]. Will retry in 5 seconds");
                //e.printStackTrace(System.out);
                retry = true; //go try again
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                }
            }
        } // end while
        return response;
    }

    public String getBearerTokenFromAuthzHeader(ContainerRequestContext context) {
        String header = context.getHeaderString("Authorization");
        return getBearerTokenFromAuthzHeaderString(header);
    }

    public String getBearerTokenFromAuthzHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return getBearerTokenFromAuthzHeaderString(header);
    }

    public String getBearerTokenFromAuthzHeaderString(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    public HttpURLConnection createConnection(String requestMethod, String apiUrl, String userName, String passwordOrPat) throws IOException, KabaneroSecurityException {
        if (userName == null || passwordOrPat == null) {
            throw new KabaneroSecurityException(HttpServletResponse.SC_BAD_REQUEST, "Cannot create a connection to [" + apiUrl + "] because the user name or password/personal access token is null.");
        }
        HttpURLConnection connection = null;
        if (apiUrl.toLowerCase().startsWith("https")) {
            connection = getHttpsConnection(requestMethod, apiUrl);
        } else {
            connection = getHttpConnection(requestMethod, apiUrl);
        }
        String encodedCredentials = new String(Base64.getEncoder().encode((userName + ":" + passwordOrPat).getBytes()));
        connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
        return connection;
    }

    public HttpsURLConnection getHttpsConnection(String requestMethod, String apiUrl) throws IOException, KabaneroSecurityException {
        SSLSocketFactory factory = null;
        try {
            JSSEHelper jsseHelper = JSSEHelper.getInstance();
            SSLContext context = jsseHelper.getSSLContext(null, null, null);
            factory = context.getSocketFactory();
        } catch (Exception e) {
            throw new KabaneroSecurityException("Failed to get SSL socket factory for connection to [" + apiUrl + "] API. " + e, e);
        }
        URL url = new URL(apiUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(factory);
        connection.setRequestMethod(requestMethod);
        return connection;
    }

    public HttpURLConnection getHttpConnection(String requestMethod, String apiUrl) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(requestMethod);
        return connection;
    }

    public String readConnectionResponse(HttpURLConnection con) throws IOException {
        InputStream responseStream = getResponseStream(con);
        BufferedReader in = new BufferedReader(new InputStreamReader(responseStream, "UTF-8"));
        String line;
        String response = "";
        while ((line = in.readLine()) != null) {
            response += line;
        }
        in.close();
        return response;
    }

    public InputStream getResponseStream(HttpURLConnection con) throws IOException {
        InputStream responseStream = null;
        int responseCode = con.getResponseCode();
        if (responseCode < 400) {
            responseStream = con.getInputStream();
        } else {
            responseStream = con.getErrorStream();
        }
        return responseStream;
    }

}
