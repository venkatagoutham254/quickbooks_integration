package aforo.quickbooks.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * OpenAPI/Swagger configuration with JWT Bearer token authentication
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI quickBooksOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuickBooks Integration API")
                        .description("API for integrating with QuickBooks Online - OAuth, Customer/Invoice/Payment sync. "
                                + "Authentication: Use JWT token from organization service login. "
                                + "Click 'Authorize' button and enter: Bearer {your-jwt-token}")
                        .version("1.0.0")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))
                        .contact(new Contact()
                                .name("Aforo")
                                .email("support@aforo.com")))
                // Add security scheme for Bearer token
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT token from organization service login. "
                                        + "Example: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")))
                // Apply security globally to all endpoints
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
