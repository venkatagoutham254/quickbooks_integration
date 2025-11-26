package aforo.quickbooks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for QuickBooks OAuth 2.0 authentication.
 */
@Configuration
@ConfigurationProperties(prefix = "quickbooks.oauth")
@Data
public class QuickBooksOAuthConfig {
    
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope;
    private String authorizationUrl;
    private String tokenUrl;
    private String revokeUrl;
    private String userInfoUrl;
    
    /**
     * Build the full authorization URL with parameters.
     */
    public String buildAuthorizationUrl(String state) {
        return String.format("%s?client_id=%s&scope=%s&redirect_uri=%s&response_type=code&state=%s",
                authorizationUrl, clientId, scope, redirectUri, state);
    }
}
