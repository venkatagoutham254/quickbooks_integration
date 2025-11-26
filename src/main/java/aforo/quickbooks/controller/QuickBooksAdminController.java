package aforo.quickbooks.controller;

import aforo.quickbooks.client.OrganizationServiceClient;
import aforo.quickbooks.dto.AforoCustomerRequest;
import aforo.quickbooks.dto.OrganizationCustomerDTO;
import aforo.quickbooks.entity.QuickBooksMapping;
import aforo.quickbooks.mapper.CustomerMapper;
import aforo.quickbooks.repository.QuickBooksConnectionRepository;
import aforo.quickbooks.repository.QuickBooksMappingRepository;
import aforo.quickbooks.security.TenantContext;
import aforo.quickbooks.service.QuickBooksApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin controller for QuickBooks management operations
 * All admin endpoints for bulk sync, statistics, and management
 */
@RestController
@RequestMapping("/api/quickbooks/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QuickBooks Admin", description = "Admin endpoints for QuickBooks management and bulk operations")
public class QuickBooksAdminController {

    private final QuickBooksApiService quickBooksApiService;
    private final QuickBooksConnectionRepository connectionRepository;
    private final QuickBooksMappingRepository mappingRepository;
    private final OrganizationServiceClient organizationServiceClient;
    private final CustomerMapper customerMapper;

    /**
     * Bulk sync all ACTIVE customers from organization to QuickBooks
     *
     * POST /api/quickbooks/admin/bulk-sync-customers
     *
     * This endpoint:
     * 1. Fetches all ACTIVE customers from organization service
     * 2. Checks which ones are already synced (in mapping table)
     * 3. Syncs only the customers NOT yet in QuickBooks
     * 4. Returns detailed statistics
     */
    @PostMapping("/bulk-sync-customers")
    @Operation(summary = "Bulk sync customers to QuickBooks",
              description = "Fetches all ACTIVE customers from organization service and syncs them to QuickBooks. " +
                           "Only syncs customers that aren't already synced. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> bulkSyncCustomers(HttpServletRequest request) {
        Long organizationId = TenantContext.require();
        String authToken = request.getHeader("Authorization");

        log.info("🔄 Admin bulk sync triggered for organization {}", organizationId);

        // Check if QuickBooks is connected
        boolean isConnected = connectionRepository.existsByOrganizationId(organizationId);
        if (!isConnected) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "QuickBooks not connected for organization " + organizationId);
            response.put("action", "Please connect QuickBooks first using GET /api/quickbooks/connect");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Fetch all ACTIVE customers from organization service
            List<OrganizationCustomerDTO> activeCustomers = organizationServiceClient
                    .getActiveCustomers(authToken, organizationId);

            log.info("📊 Found {} ACTIVE customers in organization service", activeCustomers.size());

            // Get already synced customer IDs
            List<QuickBooksMapping> existingMappings = mappingRepository
                    .findByOrganizationIdAndEntityType(organizationId, QuickBooksMapping.EntityType.CUSTOMER);

            Set<String> syncedCustomerIds = existingMappings.stream()
                    .map(QuickBooksMapping::getAforoId)
                    .collect(Collectors.toSet());

            log.info("📊 {} customers already synced to QuickBooks", syncedCustomerIds.size());

            // Filter to get customers that need to be synced
            List<OrganizationCustomerDTO> customersToSync = activeCustomers.stream()
                    .filter(customer -> !syncedCustomerIds.contains(customer.getAforoId()))
                    .toList();

            log.info("🎯 {} customers need to be synced", customersToSync.size());

            // Sync each customer
            int successCount = 0;
            int failureCount = 0;
            List<Map<String, Object>> syncResults = new ArrayList<>();

            for (OrganizationCustomerDTO customer : customersToSync) {
                try {
                    log.info("📤 Syncing customer: {} ({})", customer.getCustomerName(), customer.getAforoId());

                    // Convert to QuickBooks format
                    AforoCustomerRequest customerRequest = customerMapper.toAforoCustomerRequest(customer);

                    // Sync to QuickBooks (handles both create and update)
                    String quickBooksId = quickBooksApiService.syncCustomer(
                            organizationId,
                            customer.getAforoId(),
                            customerRequest
                    );

                    successCount++;

                    Map<String, Object> result = new HashMap<>();
                    result.put("customerId", customer.getCustomerId());
                    result.put("aforoId", customer.getAforoId());
                    result.put("customerName", customer.getCustomerName());
                    result.put("quickBooksId", quickBooksId);
                    result.put("status", "SUCCESS");
                    syncResults.add(result);

                    log.info("✅ Successfully synced customer: {} → QB ID: {}", customer.getAforoId(), quickBooksId);

                } catch (Exception e) {
                    failureCount++;

                    Map<String, Object> result = new HashMap<>();
                    result.put("customerId", customer.getCustomerId());
                    result.put("aforoId", customer.getAforoId());
                    result.put("customerName", customer.getCustomerName());
                    result.put("status", "FAILED");
                    result.put("error", e.getMessage());
                    syncResults.add(result);

                    log.error("❌ Failed to sync customer {}: {}", customer.getAforoId(), e.getMessage());
                }
            }

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("organizationId", organizationId);
            response.put("totalActiveCustomers", activeCustomers.size());
            response.put("alreadySynced", syncedCustomerIds.size());
            response.put("attemptedToSync", customersToSync.size());
            response.put("successCount", successCount);
            response.put("failureCount", failureCount);
            response.put("syncResults", syncResults);
            response.put("message", String.format("Bulk sync completed: %d succeeded, %d failed out of %d customers",
                    successCount, failureCount, customersToSync.size()));

            log.info("✅ Bulk sync completed: {} succeeded, {} failed", successCount, failureCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Bulk sync failed: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Bulk sync failed: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Manually trigger sync for a specific customer
     *
     * POST /api/quickbooks/admin/sync-customer/{customerId}
     *
     * Useful for:
     * - Retrying failed syncs
     * - Force re-sync of a customer
     * - Manual intervention
     */
    @PostMapping("/sync-customer/{customerId}")
    @Operation(summary = "Manually sync specific customer",
              description = "Fetches a customer from organization service and syncs to QuickBooks. " +
                           "Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> syncSpecificCustomer(
            @PathVariable Long customerId,
            HttpServletRequest request) {

        Long organizationId = TenantContext.require();
        String authToken = request.getHeader("Authorization");

        log.info("🔄 Manual sync triggered for customer {} in organization {}", customerId, organizationId);

        // Check QuickBooks connection
        boolean isConnected = connectionRepository.existsByOrganizationId(organizationId);
        if (!isConnected) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "QuickBooks not connected");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Fetch customer from organization service
            OrganizationCustomerDTO customer = organizationServiceClient.getCustomer(authToken, customerId);

            // Check if customer is ACTIVE
            if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("customerId", customerId);
                response.put("status", customer.getStatus());
                response.put("message", "Customer must be ACTIVE to sync. Current status: " + customer.getStatus());
                return ResponseEntity.badRequest().body(response);
            }

            // Check if already synced
            Optional<QuickBooksMapping> existing = mappingRepository.findByOrganizationIdAndEntityTypeAndAforoId(
                    organizationId,
                    QuickBooksMapping.EntityType.CUSTOMER,
                    customer.getAforoId()
            );

            // Convert to QuickBooks format
            AforoCustomerRequest customerRequest = customerMapper.toAforoCustomerRequest(customer);

            // Sync to QuickBooks (handles both create and update automatically)
            boolean wasUpdate = existing.isPresent();
            
            log.info("📤 {} customer in QuickBooks: {}", 
                wasUpdate ? "Updating" : "Creating", customer.getAforoId());
            
            String quickBooksId = quickBooksApiService.syncCustomer(
                    organizationId,
                    customer.getAforoId(),
                    customerRequest
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("customerId", customerId);
            response.put("aforoId", customer.getAforoId());
            response.put("customerName", customer.getCustomerName());
            response.put("quickBooksId", quickBooksId);
            response.put("action", wasUpdate ? "UPDATE" : "CREATE");
            response.put("message", String.format("Customer %s successfully %s in QuickBooks",
                    customer.getCustomerName(), wasUpdate ? "updated" : "created"));

            log.info("✅ Customer {} successfully synced to QuickBooks", customer.getAforoId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to sync customer {}: {}", customerId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("customerId", customerId);
            response.put("message", "Sync failed: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get customer sync overview
     *
     * GET /api/quickbooks/admin/customer-overview
     *
     * Shows:
     * - Total ACTIVE customers in organization
     * - How many are synced to QuickBooks
     * - How many are NOT synced
     * - List of un-synced customer IDs
     */
    @GetMapping("/customer-overview")
    @Operation(summary = "Get customer sync overview",
              description = "Shows ACTIVE customers in organization vs synced to QuickBooks. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> getCustomerOverview(HttpServletRequest request) {
        Long organizationId = TenantContext.require();
        String authToken = request.getHeader("Authorization");

        log.info("📊 Fetching customer overview for organization {}", organizationId);

        try {
            // Get all ACTIVE customers from organization service
            List<OrganizationCustomerDTO> activeCustomers = organizationServiceClient
                    .getActiveCustomers(authToken, organizationId);

            // Get synced customers from mapping table
            List<QuickBooksMapping> syncedMappings = mappingRepository
                    .findByOrganizationIdAndEntityType(organizationId, QuickBooksMapping.EntityType.CUSTOMER);

            Set<String> syncedCustomerIds = syncedMappings.stream()
                    .map(QuickBooksMapping::getAforoId)
                    .collect(Collectors.toSet());

            // Find un-synced customers
            List<Map<String, Object>> unsyncedCustomers = activeCustomers.stream()
                    .filter(customer -> !syncedCustomerIds.contains(customer.getAforoId()))
                    .map(customer -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("customerId", customer.getCustomerId());
                        info.put("aforoId", customer.getAforoId());
                        info.put("customerName", customer.getCustomerName());
                        info.put("email", customer.getPrimaryEmail());
                        return info;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("organizationId", organizationId);
            response.put("totalActiveCustomers", activeCustomers.size());
            response.put("syncedToQuickBooks", syncedCustomerIds.size());
            response.put("notSyncedToQuickBooks", unsyncedCustomers.size());
            response.put("unsyncedCustomers", unsyncedCustomers);
            response.put("syncPercentage", activeCustomers.isEmpty() ? 0 :
                    (double) syncedCustomerIds.size() / activeCustomers.size() * 100);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to get customer overview: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get overview: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get list of customers that are NOT synced to QuickBooks
     *
     * GET /api/quickbooks/admin/unsynced-customers
     *
     * Useful for:
     * - Seeing which customers need manual attention
     * - Planning bulk sync operations
     * - Debugging sync issues
     */
    @GetMapping("/unsynced-customers")
    @Operation(summary = "Get un-synced customers",
              description = "Returns list of ACTIVE customers that are NOT synced to QuickBooks. Organization ID extracted from JWT token.")
    public ResponseEntity<Map<String, Object>> getUnsyncedCustomers(HttpServletRequest request) {
        Long organizationId = TenantContext.require();
        String authToken = request.getHeader("Authorization");

        log.info("📋 Fetching un-synced customers for organization {}", organizationId);

        try {
            // Get all ACTIVE customers
            List<OrganizationCustomerDTO> activeCustomers = organizationServiceClient
                    .getActiveCustomers(authToken, organizationId);

            // Get synced customer IDs
            List<QuickBooksMapping> syncedMappings = mappingRepository
                    .findByOrganizationIdAndEntityType(organizationId, QuickBooksMapping.EntityType.CUSTOMER);

            Set<String> syncedCustomerIds = syncedMappings.stream()
                    .map(QuickBooksMapping::getAforoId)
                    .collect(Collectors.toSet());

            // Filter un-synced customers
            List<Map<String, Object>> unsyncedCustomers = activeCustomers.stream()
                    .filter(customer -> !syncedCustomerIds.contains(customer.getAforoId()))
                    .map(customer -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("customerId", customer.getCustomerId());
                        info.put("aforoId", customer.getAforoId());
                        info.put("customerName", customer.getCustomerName());
                        info.put("companyName", customer.getCompanyName());
                        info.put("email", customer.getPrimaryEmail());
                        info.put("phone", customer.getPhoneNumber());
                        return info;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("organizationId", organizationId);
            response.put("total", unsyncedCustomers.size());
            response.put("customers", unsyncedCustomers);
            response.put("message", unsyncedCustomers.size() + " customers are ACTIVE but not synced to QuickBooks");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to get un-synced customers: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get un-synced customers: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }
}
