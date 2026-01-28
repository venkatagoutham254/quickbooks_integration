package aforo.quickbooks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Organization Service integration
 */
@Configuration
@ConfigurationProperties(prefix = "aforo.organization-service")
@Data
public class OrganizationServiceProperties {
    
    /**
     * Base URL of the organization service
     * Example: http://localhost:8081 or http://host.docker.internal:8081
     */
    private String baseUrl = "http://org.dev.aforo.space:8081";
    
    /**
     * Timeout in seconds for organization service calls
     */
    private int timeoutSeconds = 30;
}
