package aforo.quickbooks.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for CORS support
 * This ensures CORS works properly for Swagger UI and other browser-based clients
 * 
 * CORS origins are now externalized via application.yml (aforo.cors.allowed-origins)
 * and can be overridden using CORS_ALLOWED_ORIGINS environment variable.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(corsProperties.getAllowedOriginsList().toArray(new String[0]))
                .allowedMethods(corsProperties.getAllowedMethodsList().toArray(new String[0]))
                .allowedHeaders(corsProperties.getAllowedHeadersList().toArray(new String[0]))
                .exposedHeaders("Authorization", "Content-Type", "X-Total-Count")
                .allowCredentials(corsProperties.isAllowCredentials())
                .maxAge(3600);
    }
}
