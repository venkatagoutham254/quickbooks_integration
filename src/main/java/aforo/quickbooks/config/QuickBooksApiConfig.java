package aforo.quickbooks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for QuickBooks API endpoints.
 */
@Configuration
@ConfigurationProperties(prefix = "quickbooks.api")
@Data
public class QuickBooksApiConfig {
    
    private String baseUrl;
    private String version;
    private String environment;
    private String minorVersion;
    
    /**
     * Build company API URL.
     */
    public String getCompanyUrl(String realmId) {
        return String.format("%s/%s/company/%s", baseUrl, version, realmId);
    }
    
    /**
     * Get full endpoint URL for a specific resource.
     */
    public String getResourceUrl(String realmId, String resource) {
        return String.format("%s/%s", getCompanyUrl(realmId), resource);
    }
}
