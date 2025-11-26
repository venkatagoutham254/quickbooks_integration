package aforo.quickbooks.controller;

import aforo.quickbooks.entity.QuickBooksConnection;
import aforo.quickbooks.security.TenantContext;
import aforo.quickbooks.service.QuickBooksOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for QuickBooks OAuth 2.0 authentication.
 */
@RestController
@RequestMapping("/api/quickbooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QuickBooks Authentication", description = "OAuth endpoints for QuickBooks integration")
public class QuickBooksAuthController {

    private final QuickBooksOAuthService oauthService;

    @GetMapping("/connect")
    @Operation(summary = "Initiate QuickBooks connection", 
              description = "Generates OAuth authorization URL for QuickBooks. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, String>> initiateConnection() {
        
        Long organizationId = TenantContext.require();
        log.info("Initiating QuickBooks connection for org: {}", organizationId);
        
        String authUrl = oauthService.generateAuthorizationUrl(organizationId);
        
        Map<String, String> response = new HashMap<>();
        response.put("authUrl", authUrl);
        response.put("message", "Redirect user to authUrl to authorize");
        response.put("organizationId", organizationId.toString());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth callback", 
              description = "Handles QuickBooks OAuth callback and exchanges code for tokens")
    public ResponseEntity<Map<String, String>> handleCallback(
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam String realmId) {
        
        log.info("Received QuickBooks OAuth callback - realmId: {}", realmId);
        
        try {
            // Extract organizationId from state
            String[] stateParts = state.split(":");
            Long organizationId = Long.parseLong(stateParts[1]);
            
            QuickBooksConnection connection = oauthService.exchangeCodeForTokens(
                code, realmId, organizationId);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "QuickBooks connected successfully!");
            response.put("realmId", connection.getRealmId());
            response.put("organizationId", organizationId.toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to complete QuickBooks OAuth: {}", e.getMessage(), e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to connect QuickBooks: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Disconnect QuickBooks", 
              description = "Revokes QuickBooks connection for organization. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, String>> disconnect() {
        
        Long organizationId = TenantContext.require();
        log.info("Disconnecting QuickBooks for org: {}", organizationId);
        
        try {
            oauthService.disconnect(organizationId);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "QuickBooks disconnected successfully");
            response.put("organizationId", organizationId.toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to disconnect QuickBooks: {}", e.getMessage(), e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to disconnect: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Check connection status", 
              description = "Check if QuickBooks is connected for organization. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> checkStatus() {
        
        Long organizationId = TenantContext.require();
        
        try {
            QuickBooksConnection connection = oauthService.getActiveConnection(organizationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("connected", true);
            response.put("organizationId", organizationId);
            response.put("realmId", connection.getRealmId());
            response.put("companyName", connection.getCompanyName());
            response.put("connectedSince", connection.getCreatedAt().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("connected", false);
            response.put("organizationId", organizationId);
            response.put("message", e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }
}
