package kabasec;

import java.util.NoSuchElementException;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * allows github base url to be passed in through mp-config 
 * so private git repos can be used, example: https://github.mycompany.com
 *
 */
public class Config {
    private String gitHubApiUrlBase = null;
    
    public String getApiUrlBase() {
        init();
        return gitHubApiUrlBase;
    }
    
    public String getUserInfoUrl() {
       init();
       return gitHubApiUrlBase  + "/user";
    }
    public String getEmailUrl() {
       init();
       return gitHubApiUrlBase  + "/emails";
    }
    public String getTeamsUrl() {
       init();
       return gitHubApiUrlBase  + "/user/teams";
    }
    
    
    private void init() {
        if (gitHubApiUrlBase != null) {
            return;
        }
        org.eclipse.microprofile.config.Config mpConfig = ConfigProvider.getConfig();
        String key = null;
        try {
            key = mpConfig.getValue(Constants.GITHUB_URL_MPCONFIG_PROPERTYNAME, String.class);            
        } catch (NoSuchElementException e) {
            // it's not there
        }
        /*
        if (key == null || key.isEmpty()) {            
            key = Constants.GITHUB_API_URL_BASE;
        }
        */
        gitHubApiUrlBase = key;
    }
    
}