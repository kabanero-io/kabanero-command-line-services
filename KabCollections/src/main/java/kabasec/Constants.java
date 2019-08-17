package kabasec;

public class Constants {

    public static final String GITHUB_API_URL_BASE = "https://api.github.com";

    public static final String USERINFO_API = GITHUB_API_URL_BASE + "/user";
    public static final String EMAIL_API = GITHUB_API_URL_BASE + "/user/emails";
    public static final String TEAMS_API = GITHUB_API_URL_BASE + "/user/teams";

    public static final String LOGIN_KEY_GITHUB_USER = "gituser";
    public static final String LOGIN_KEY_GITHUB_PASSWORD_OR_PAT = "gitpat";
    public static final String PAT_JWT_CLAIM = "pat";
}
