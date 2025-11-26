package aforo.quickbooks.exception;

/**
 * Exception thrown when QuickBooks API rate limit is exceeded.
 */
public class QuickBooksRateLimitException extends QuickBooksException {
    
    public QuickBooksRateLimitException(String message) {
        super(message);
    }
    
    public QuickBooksRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
