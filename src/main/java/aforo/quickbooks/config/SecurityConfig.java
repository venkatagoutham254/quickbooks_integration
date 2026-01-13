package aforo.quickbooks.config;

import aforo.quickbooks.security.JwtTenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration for the QuickBooks Integration service.
 * 
 * CORS configuration is now externalized via application.yml (aforo.cors.*)
 * and can be overridden using CORS_ALLOWED_ORIGINS environment variable.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTenantFilter jwtTenantFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/quickbooks/callback/**",
                    "/api/health/**",
                    "/v3/api-docs/**",
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                .anyRequest().permitAll() // Allow all for now, JWT filter extracts org ID
            )
            .addFilterBefore(jwtTenantFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Use origins from CorsProperties (externalized configuration)
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginsList());
        configuration.setAllowedMethods(corsProperties.getAllowedMethodsList());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeadersList());
        configuration.setExposedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Total-Count"));
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
