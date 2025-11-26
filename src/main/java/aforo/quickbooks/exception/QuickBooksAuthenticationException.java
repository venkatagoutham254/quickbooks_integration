package aforo.quickbooks.exception;

/**
 * Exception thrown when QuickBooks authentication fails.
 */
public class QuickBooksAuthenticationException extends QuickBooksException {
    
    public QuickBooksAuthenticationException(String message) {
        super(message);
    }
    
    public QuickBooksAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
