package kabasec;

import javax.servlet.http.HttpServletResponse;

public class IncompleteCredentialsException extends KabaneroSecurityException {

    private static final long serialVersionUID = 1L;

    public IncompleteCredentialsException(String message) {
        super(HttpServletResponse.SC_BAD_REQUEST, message);
    }

}
