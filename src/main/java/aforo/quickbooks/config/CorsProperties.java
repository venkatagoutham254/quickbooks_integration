package aforo.quickbooks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for CORS settings.
 * Maps to aforo.cors.* properties in application.yml
 */
@Data
@Component
@ConfigurationProperties(prefix = "aforo.cors")
public class CorsProperties {

    /**
     * Comma-separated list of allowed origins for CORS.
     * Can be exact origins or patterns (e.g., "http://localhost:*")
     * 
     * Example: http://localhost:*,https://aforo.space,https://quickbooks.aforo.space
     */
    private String allowedOrigins;

    /**
     * Comma-separated list of allowed HTTP methods.
     * Default: GET,POST,PUT,PATCH,DELETE,OPTIONS
     */
    private String allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";

    /**
     * Comma-separated list of allowed headers.
     * Default: Authorization,Content-Type,X-Organization-Id
     */
    private String allowedHeaders = "Authorization,Content-Type,X-Organization-Id";

    /**
     * Whether to allow credentials (cookies, authorization headers).
     * Default: true
     */
    private boolean allowCredentials = true;

    /**
     * Parse allowed origins from comma-separated string to list.
     */
    public List<String> getAllowedOriginsList() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return List.of();
        }
        return List.of(allowedOrigins.split(","));
    }

    /**
     * Parse allowed methods from comma-separated string to list.
     */
    public List<String> getAllowedMethodsList() {
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            return List.of();
        }
        return List.of(allowedMethods.split(","));
    }

    /**
     * Parse allowed headers from comma-separated string to list.
     */
    public List<String> getAllowedHeadersList() {
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            return List.of("*");
        }
        return List.of(allowedHeaders.split(","));
    }
}
