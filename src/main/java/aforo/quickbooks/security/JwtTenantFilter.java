package aforo.quickbooks.security;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to extract organization ID from JWT token and store in TenantContext
 * This allows automatic tenant scoping without passing organizationId in request parameters
 */
@Component
@Slf4j
public class JwtTenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            // Extract JWT token from Authorization header
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    // Parse JWT token
                    SignedJWT signedJWT = SignedJWT.parse(token);
                    
                    // Extract organization ID from claims - try both possible claim names
                    Object orgIdClaim = signedJWT.getJWTClaimsSet().getClaim("organizationId");
                    if (orgIdClaim == null) {
                        // Try alternative claim name used by organization service
                        orgIdClaim = signedJWT.getJWTClaimsSet().getClaim("orgId");
                    }
                    
                    if (orgIdClaim != null) {
                        Long organizationId = null;
                        
                        // Handle different claim types
                        if (orgIdClaim instanceof Number) {
                            organizationId = ((Number) orgIdClaim).longValue();
                        } else if (orgIdClaim instanceof String) {
                            organizationId = Long.parseLong((String) orgIdClaim);
                        }
                        
                        if (organizationId != null) {
                            TenantContext.setOrganizationId(organizationId);
                            log.debug("Extracted organization ID {} from JWT token (claim: {})", 
                                organizationId, orgIdClaim);
                        }
                    } else {
                        log.warn("No organizationId or orgId claim found in JWT token");
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to parse JWT token: {}", e.getMessage());
                }
            }
            
            // Continue filter chain
            filterChain.doFilter(request, response);
            
        } finally {
            // Clear context after request completes
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip filter for public endpoints
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || 
               path.startsWith("/v3/api-docs") || 
               path.startsWith("/api-docs") ||
               path.startsWith("/api/health") ||
               path.equals("/api/quickbooks/callback"); // OAuth callback doesn't have JWT
    }
}
