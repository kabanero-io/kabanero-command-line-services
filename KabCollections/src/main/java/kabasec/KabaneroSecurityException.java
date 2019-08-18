package kabasec;

import javax.servlet.http.HttpServletResponse;

public class KabaneroSecurityException extends Exception {

    private static final long serialVersionUID = 1L;

    private int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

    public KabaneroSecurityException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public KabaneroSecurityException(String message, Throwable cause) {
        super(message, cause);
        if (cause instanceof KabaneroSecurityException) {
            this.statusCode = ((KabaneroSecurityException) cause).getStatusCode();
        }
    }

    int getStatusCode() {
        return statusCode;
    }

}
