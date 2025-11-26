package aforo.quickbooks.service;

import aforo.quickbooks.config.QuickBooksOAuthConfig;
import aforo.quickbooks.dto.QuickBooksTokenResponse;
import aforo.quickbooks.entity.QuickBooksConnection;
import aforo.quickbooks.exception.QuickBooksAuthenticationException;
import aforo.quickbooks.repository.QuickBooksConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service handling QuickBooks OAuth 2.0 authentication flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuickBooksOAuthService {

    private final QuickBooksOAuthConfig oauthConfig;
    private final QuickBooksConnectionRepository connectionRepository;
    private final WebClient webClient;

    /**
     * Generate authorization URL for OAuth flow.
     */
    public String generateAuthorizationUrl(Long organizationId) {
        String state = UUID.randomUUID().toString() + ":" + organizationId;
        return oauthConfig.buildAuthorizationUrl(state);
    }

    /**
     * Exchange authorization code for access and refresh tokens.
     */
    @Transactional
    public QuickBooksConnection exchangeCodeForTokens(String code, String realmId, Long organizationId) {
        log.info("Exchanging authorization code for tokens for org: {}", organizationId);
        
        try {
            QuickBooksTokenResponse tokenResponse = requestTokens("authorization_code", code, null);
            
            return saveConnection(organizationId, realmId, tokenResponse);
        } catch (Exception e) {
            log.error("Failed to exchange code for tokens: {}", e.getMessage(), e);
            throw new QuickBooksAuthenticationException("Failed to authenticate with QuickBooks", e);
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    @Transactional
    public QuickBooksConnection refreshAccessToken(QuickBooksConnection connection) {
        log.info("Refreshing access token for org: {}", connection.getOrganizationId());
        
        try {
            QuickBooksTokenResponse tokenResponse = requestTokens(
                "refresh_token", null, connection.getRefreshToken());
            
            return updateConnectionTokens(connection, tokenResponse);
        } catch (Exception e) {
            log.error("Failed to refresh token for org {}: {}", 
                     connection.getOrganizationId(), e.getMessage(), e);
            throw new QuickBooksAuthenticationException("Failed to refresh QuickBooks token", e);
        }
    }

    /**
     * Get active connection for organization, refreshing if needed.
     */
    @Transactional
    public QuickBooksConnection getActiveConnection(Long organizationId) {
        QuickBooksConnection connection = connectionRepository.findByOrganizationId(organizationId)
            .orElseThrow(() -> new QuickBooksAuthenticationException(
                "No QuickBooks connection found for organization: " + organizationId));
        
        if (!connection.getIsActive()) {
            throw new QuickBooksAuthenticationException(
                "QuickBooks connection is inactive for organization: " + organizationId);
        }
        
        if (connection.isTokenExpired()) {
            log.info("Token expired for org {}, refreshing...", organizationId);
            connection = refreshAccessToken(connection);
        }
        
        return connection;
    }

    /**
     * Disconnect QuickBooks for an organization.
     */
    @Transactional
    public void disconnect(Long organizationId) {
        QuickBooksConnection connection = connectionRepository.findByOrganizationId(organizationId)
            .orElseThrow(() -> new QuickBooksAuthenticationException(
                "No QuickBooks connection found for organization: " + organizationId));
        
        try {
            revokeToken(connection.getRefreshToken());
        } catch (Exception e) {
            log.warn("Failed to revoke token: {}", e.getMessage());
        }
        
        connection.setIsActive(false);
        connectionRepository.save(connection);
        log.info("Disconnected QuickBooks for org: {}", organizationId);
    }

    /**
     * Scheduled task to refresh tokens before expiration.
     */
    @Scheduled(fixedDelay = 3000000) // Every 50 minutes
    @Transactional
    public void refreshExpiringTokens() {
        Instant expiryThreshold = Instant.now().plusSeconds(600); // 10 minutes buffer
        List<QuickBooksConnection> connections = connectionRepository
            .findByExpiresAtBefore(expiryThreshold);
        
        log.info("Found {} connections with expiring tokens", connections.size());
        
        for (QuickBooksConnection connection : connections) {
            try {
                refreshAccessToken(connection);
                log.info("Refreshed token for org: {}", connection.getOrganizationId());
            } catch (Exception e) {
                log.error("Failed to refresh token for org {}: {}", 
                         connection.getOrganizationId(), e.getMessage());
            }
        }
    }

    // Private helper methods
    
    private QuickBooksTokenResponse requestTokens(String grantType, String code, String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", grantType);
        formData.add("redirect_uri", oauthConfig.getRedirectUri());
        
        if ("authorization_code".equals(grantType)) {
            formData.add("code", code);
        } else {
            formData.add("refresh_token", refreshToken);
        }
        
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
            (oauthConfig.getClientId() + ":" + oauthConfig.getClientSecret()).getBytes());
        
        return webClient.post()
            .uri(oauthConfig.getTokenUrl())
            .header("Authorization", authHeader)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(QuickBooksTokenResponse.class)
            .block();
    }

    private QuickBooksConnection saveConnection(Long organizationId, String realmId, 
                                                 QuickBooksTokenResponse tokenResponse) {
        QuickBooksConnection connection = connectionRepository.findByOrganizationId(organizationId)
            .orElse(new QuickBooksConnection());
        
        connection.setOrganizationId(organizationId);
        connection.setRealmId(realmId);
        connection.setAccessToken(tokenResponse.getAccessToken());
        connection.setRefreshToken(tokenResponse.getRefreshToken());
        connection.setExpiresAt(Instant.now().plusSeconds(tokenResponse.getExpiresIn()));
        connection.setRefreshTokenExpiresAt(Instant.now().plusSeconds(
            tokenResponse.getRefreshTokenExpiresIn()));
        connection.setIsActive(true);
        
        return connectionRepository.save(connection);
    }

    private QuickBooksConnection updateConnectionTokens(QuickBooksConnection connection, 
                                                        QuickBooksTokenResponse tokenResponse) {
        connection.setAccessToken(tokenResponse.getAccessToken());
        connection.setRefreshToken(tokenResponse.getRefreshToken());
        connection.setExpiresAt(Instant.now().plusSeconds(tokenResponse.getExpiresIn()));
        connection.setRefreshTokenExpiresAt(Instant.now().plusSeconds(
            tokenResponse.getRefreshTokenExpiresIn()));
        
        return connectionRepository.save(connection);
    }

    private void revokeToken(String token) {
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
            (oauthConfig.getClientId() + ":" + oauthConfig.getClientSecret()).getBytes());
        
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("token", token);
        
        webClient.post()
            .uri(oauthConfig.getRevokeUrl())
            .header("Authorization", authHeader)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
}
