package aforo.quickbooks.controller;

import aforo.quickbooks.config.QuickBooksApiConfig;
import aforo.quickbooks.entity.QuickBooksConnection;
import aforo.quickbooks.entity.QuickBooksMapping;
import aforo.quickbooks.repository.QuickBooksConnectionRepository;
import aforo.quickbooks.repository.QuickBooksMappingRepository;
import aforo.quickbooks.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for QuickBooks sync status and analytics
 */
@RestController
@RequestMapping("/api/quickbooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QuickBooks Sync Status", description = "Get accurate sync status and statistics")
public class QuickBooksSyncStatusController {

    private final QuickBooksMappingRepository mappingRepository;
    private final QuickBooksConnectionRepository connectionRepository;
    private final QuickBooksApiConfig apiConfig;

    /**
     * Get accurate sync status - shows what's actually synced to QuickBooks
     * 
     * GET /api/quickbooks/sync-stats
     * 
     * Response:
     * {
     *   "organizationId": 4,
     *   "connectedToQuickBooks": true,
     *   "totalSyncedEntities": 1,
     *   "syncedCustomers": 1,
     *   "syncedInvoices": 0,
     *   "syncedPayments": 0,
     *   "customerIds": ["CUST-123"]
     * }
     */
    @GetMapping("/sync-stats")
    @Operation(summary = "Get sync statistics", 
              description = "Returns accurate count of entities synced to QuickBooks by checking the mapping table. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> getSyncStatistics() {
        Long organizationId = TenantContext.require();
        
        log.info("📊 Fetching sync statistics for organization {}", organizationId);
        
        // Get all mappings for this organization
        List<QuickBooksMapping> allMappings = mappingRepository.findByOrganizationId(organizationId);
        
        // Count by entity type
        long customerCount = allMappings.stream()
            .filter(m -> m.getEntityType() == QuickBooksMapping.EntityType.CUSTOMER)
            .count();
        
        long invoiceCount = allMappings.stream()
            .filter(m -> m.getEntityType() == QuickBooksMapping.EntityType.INVOICE)
            .count();
        
        long paymentCount = allMappings.stream()
            .filter(m -> m.getEntityType() == QuickBooksMapping.EntityType.PAYMENT)
            .count();
        
        // Get list of synced customer IDs
        List<String> syncedCustomerIds = allMappings.stream()
            .filter(m -> m.getEntityType() == QuickBooksMapping.EntityType.CUSTOMER)
            .map(QuickBooksMapping::getAforoId)
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("organizationId", organizationId);
        response.put("totalSyncedEntities", allMappings.size());
        response.put("syncedCustomers", customerCount);
        response.put("syncedInvoices", invoiceCount);
        response.put("syncedPayments", paymentCount);
        response.put("syncedCustomerIds", syncedCustomerIds);
        response.put("message", customerCount + " customer(s) actually synced to QuickBooks");
        
        log.info("✅ Sync stats: {} customers, {} invoices, {} payments", 
            customerCount, invoiceCount, paymentCount);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed list of synced customers
     * 
     * GET /api/quickbooks/synced-customers
     * 
     * Response:
     * {
     *   "customers": [
     *     {
     *       "aforoId": "CUST-123",
     *       "quickbooksId": "456",
     *       "syncedAt": "2025-11-18T06:30:00Z"
     *     }
     *   ],
     *   "total": 1
     * }
     */
    @GetMapping("/synced-customers")
    @Operation(summary = "Get synced customers", 
              description = "Returns list of customers that are actually synced to QuickBooks. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> getSyncedCustomers() {
        Long organizationId = TenantContext.require();
        
        log.info("📋 Fetching synced customers for organization {}", organizationId);
        
        // Get customer mappings
        List<QuickBooksMapping> customerMappings = mappingRepository
            .findByOrganizationIdAndEntityType(organizationId, QuickBooksMapping.EntityType.CUSTOMER);
        
        // Map to response DTOs
        List<Map<String, Object>> customers = customerMappings.stream()
            .map(mapping -> {
                Map<String, Object> customerData = new HashMap<>();
                customerData.put("aforoId", mapping.getAforoId());
                customerData.put("quickbooksId", mapping.getQuickbooksId());
                customerData.put("syncedAt", mapping.getCreatedAt());
                customerData.put("lastUpdated", mapping.getUpdatedAt());
                return customerData;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("customers", customers);
        response.put("total", customers.size());
        response.put("organizationId", organizationId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Check if a specific customer is synced to QuickBooks
     * 
     * GET /api/quickbooks/customer/{customerId}/sync-status
     * 
     * Response:
     * {
     *   "customerId": "CUST-123",
     *   "isSynced": true,
     *   "quickbooksId": "456",
     *   "syncedAt": "2025-11-18T06:30:00Z"
     * }
     */
    @GetMapping("/customer/{customerId}/sync-status")
    @Operation(summary = "Check customer sync status", 
              description = "Check if a specific customer is synced to QuickBooks. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> getCustomerSyncStatus(@PathVariable String customerId) {
        Long organizationId = TenantContext.require();
        
        log.info("🔍 Checking sync status for customer {} in organization {}", customerId, organizationId);
        
        // Convert customerId to aforoId format (CUST-{id})
        String aforoId = customerId.startsWith("CUST-") ? customerId : "CUST-" + customerId;
        
        Map<String, Object> response = new HashMap<>();
        response.put("customerId", customerId);
        response.put("organizationId", organizationId);
        
        // Check if mapping exists using aforoId format
        var mapping = mappingRepository.findByOrganizationIdAndEntityTypeAndAforoId(
            organizationId, 
            QuickBooksMapping.EntityType.CUSTOMER, 
            aforoId
        );
        
        if (mapping.isPresent()) {
            response.put("isSynced", true);
            response.put("quickbooksId", mapping.get().getQuickbooksId());
            response.put("syncedAt", mapping.get().getCreatedAt());
            response.put("lastUpdated", mapping.get().getUpdatedAt());
            response.put("message", "Customer is synced to QuickBooks");
        } else {
            response.put("isSynced", false);
            response.put("message", "Customer is NOT synced to QuickBooks");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Redirect to QuickBooks company dashboard
     * 
     * GET /api/quickbooks/dashboard-redirect
     * 
     * This endpoint redirects users directly to their QuickBooks Online company dashboard.
     * The organization ID is extracted from the JWT token to ensure users only access
     * their own QuickBooks company.
     * 
     * Response: 302 Redirect to https://app.qbo.intuit.com/app/{realmId}
     */
    @GetMapping("/dashboard-redirect")
    @Operation(summary = "Redirect to QuickBooks Dashboard", 
              description = "Redirects the authenticated user to their QuickBooks Online company dashboard. Organization ID extracted from JWT token.")
    public ResponseEntity<?> redirectToQuickBooksDashboard(
            @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug) {
        Long organizationId = TenantContext.require();
        
        log.info("🚀 Redirecting user to QuickBooks dashboard for organization {}", organizationId);
        
        try {
            // Get QuickBooks connection for this organization
            var connection = connectionRepository.findByOrganizationId(organizationId);
            
            if (connection.isEmpty() || !connection.get().getIsActive()) {
                log.warn("⚠️ No active QuickBooks connection found for organization {}", organizationId);
                
                // Return error response instead of redirect
                HttpHeaders headers = new HttpHeaders();
                headers.add("X-Error-Message", "QuickBooks not connected for this organization");
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .headers(headers)
                    .build();
            }
            
            QuickBooksConnection qbConnection = connection.get();
            String realmId = qbConnection.getRealmId();

            // Decide QuickBooks UI base URL based on environment (sandbox vs production)
            String environment = apiConfig.getEnvironment();
            String uiBaseUrl;
            if ("sandbox".equalsIgnoreCase(environment)) {
                // QuickBooks Online sandbox UI
                uiBaseUrl = "https://sandbox.qbo.intuit.com";
            } else {
                // Default to production host
                uiBaseUrl = "https://app.qbo.intuit.com";
            }

            // Construct QuickBooks Online dashboard URL (homepage for the specific company)
            String dashboardUrl = uiBaseUrl + "/app/homepage?companyId=" + realmId;

            // When debug=true (e.g. from Swagger), return JSON instead of a 302 redirect
            if (debug) {
                Map<String, Object> body = new HashMap<>();
                body.put("organizationId", organizationId);
                body.put("realmId", realmId);
                body.put("environment", environment);
                body.put("dashboardUrl", dashboardUrl);

                log.info("✅ Returning QuickBooks dashboard URL in JSON (debug mode): {} (Realm: {})", dashboardUrl, realmId);

                return ResponseEntity.ok(body);
            }

            log.info("✅ Redirecting to QuickBooks dashboard: {} (Realm: {})", dashboardUrl, realmId);
            
            // Create redirect response
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", dashboardUrl);
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("X-Organization-Id", organizationId.toString());
            headers.add("X-QuickBooks-Realm-Id", realmId);
            
            return ResponseEntity.status(HttpStatus.FOUND)
                .headers(headers)
                .build();
                
        } catch (Exception e) {
            log.error("❌ Failed to redirect to QuickBooks dashboard for organization {}: {}", 
                organizationId, e.getMessage(), e);
            
            // Return error response
            if (debug) {
                Map<String, Object> body = new HashMap<>();
                body.put("organizationId", organizationId);
                body.put("message", "Failed to redirect to QuickBooks dashboard");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(body);
            } else {
                HttpHeaders headers = new HttpHeaders();
                headers.add("X-Error-Message", "Failed to redirect to QuickBooks dashboard");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(headers)
                    .build();
            }
        }
    }
}
