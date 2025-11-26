package aforo.quickbooks.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for QuickBooks integration.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(QuickBooksAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            QuickBooksAuthenticationException ex) {
        log.error("Authentication error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Authentication failed",
            ex.getMessage()
        );
    }

    @ExceptionHandler(QuickBooksRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(
            QuickBooksRateLimitException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded",
            "Too many requests to QuickBooks API. Please try again later."
        );
    }

    @ExceptionHandler(QuickBooksException.class)
    public ResponseEntity<Map<String, Object>> handleQuickBooksException(
            QuickBooksException ex) {
        log.error("QuickBooks error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "QuickBooks integration error",
            ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "An unexpected error occurred"
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
