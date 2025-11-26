package aforo.quickbooks.controller;

import aforo.quickbooks.client.RatePlanServiceClient;
import aforo.quickbooks.dto.QuickBooksInvoiceRequest;
import aforo.quickbooks.entity.QuickBooksConnection;
import aforo.quickbooks.entity.QuickBooksMapping;
import aforo.quickbooks.mapper.InvoiceMapper;
import aforo.quickbooks.repository.QuickBooksConnectionRepository;
import aforo.quickbooks.service.QuickBooksApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Webhook controller that receives invoice notifications from metering service.
 */
@RestController
@RequestMapping("/api/quickbooks/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QuickBooks Webhooks", description = "Endpoints for receiving notifications from metering service")
public class QuickBooksWebhookController {

    private final QuickBooksConnectionRepository connectionRepository;
    private final QuickBooksApiService apiService;
    private final InvoiceMapper invoiceMapper;
    private final WebClient webClient;
    private final RatePlanServiceClient ratePlanServiceClient;
    
    @Value("${aforo.metering-service.base-url}")
    private String meteringServiceBaseUrl;

    /**
     * Receive invoice created notification from metering service.
     * 
     * Expected payload:
     * {
     *   "invoiceId": 123,
     *   "organizationId": 9,
     *   "customerId": 9,
     *   "invoiceNumber": "INV-9-9-20251121073208",
     *   "totalAmount": 210.00,
     *   "jwtToken": "eyJhbGc..."
     * }
     */
    @PostMapping("/invoice-created")
    @Operation(summary = "Receive invoice created notification", 
              description = "Called by metering service when a new invoice is created")
    public ResponseEntity<Map<String, String>> handleInvoiceCreated(@RequestBody Map<String, Object> payload) {
        
        Long invoiceId = getLong(payload, "invoiceId");
        Long organizationId = getLong(payload, "organizationId");
        String invoiceNumber = (String) payload.get("invoiceNumber");
        String jwtToken = (String) payload.get("jwtToken");
        
        log.info("📨 Received invoice created webhook for invoice {} (ID: {}) from organization {}", 
                invoiceNumber, invoiceId, organizationId);
        
        // Process asynchronously so metering service doesn't wait
        processInvoiceAsync(invoiceId, organizationId, jwtToken);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("message", "Invoice sync triggered");
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Process invoice synchronization asynchronously.
     */
    @Async
    public void processInvoiceAsync(Long invoiceId, Long organizationId, String jwtToken) {
        try {
            log.info("🔄 Starting async sync for invoice {} in organization {}", invoiceId, organizationId);
            
            // Check if QuickBooks is connected for this organization
            Optional<QuickBooksConnection> connectionOpt = connectionRepository
                    .findByOrganizationId(organizationId);

            if (connectionOpt.isEmpty() || !connectionOpt.get().getIsActive()) {
                log.info("⏭️ No active QuickBooks connection for organization {}. Skipping sync.", organizationId);
                return;
            }

            QuickBooksConnection connection = connectionOpt.get();
            log.info("✅ QuickBooks connection found for organization {} (Realm: {})", 
                    organizationId, connection.getRealmId());

            // Fetch full invoice details from metering service
            Map<String, Object> invoiceData = fetchInvoiceFromMeteringService(invoiceId, organizationId, jwtToken);
            if (invoiceData == null) {
                log.error("❌ Failed to fetch invoice {} from metering service", invoiceId);
                return;
            }

            Long customerId = getLong(invoiceData, "customerId");
            Long ratePlanId = getLong(invoiceData, "ratePlanId");
            String invoiceNumber = (String) invoiceData.get("invoiceNumber");

            // Get QuickBooks customer ID mapping
            // Use same format as OrganizationCustomerDTO.getAforoId() -> "CUST-{id}"
            String qbCustomerId = apiService.getQuickBooksId(
                    organizationId,
                    QuickBooksMapping.EntityType.CUSTOMER,
                    "CUST-" + customerId
            );

            if (qbCustomerId == null) {
                log.warn("⚠️ Customer {} not synced to QuickBooks for organization {}. Cannot create invoice.",
                        customerId, organizationId);
                // TODO: Consider auto-syncing customer here or queuing for retry
                return;
            }

            // Fetch rate plan name from rate plan service
            String ratePlanName = null;
            if (ratePlanId != null) {
                ratePlanName = ratePlanServiceClient.getRatePlanName(ratePlanId, jwtToken);
            }
            
            // If rate plan name not found, use default
            if (ratePlanName == null || ratePlanName.trim().isEmpty()) {
                log.warn("Rate plan name not found for ID {}, using default", ratePlanId);
                ratePlanName = "Subscription Service";
            }
            
            log.info("Using service item name: {}", ratePlanName);
            
            // Get or create service item with rate plan name
            String qbServiceItemId = apiService.getOrCreateServiceItemByName(connection, ratePlanName);
            
            // Map to QuickBooks format
            // All line items will use the same service item (rate plan name)
            QuickBooksInvoiceRequest qbInvoiceRequest = invoiceMapper.toQuickBooksFormat(
                    invoiceData,
                    qbCustomerId,
                    qbServiceItemId  // Use rate plan service item for all lines
            );

            // Create invoice in QuickBooks
            String qbInvoiceId = apiService.createInvoice(
                    organizationId,
                    "invoice-" + invoiceId,
                    qbInvoiceRequest
            );

            log.info("🎉 Invoice {} synced to QuickBooks successfully! QB Invoice ID: {}",
                    invoiceNumber, qbInvoiceId);

        } catch (Exception e) {
            log.error("❌ Failed to sync invoice {} to QuickBooks: {}", invoiceId, e.getMessage(), e);
        }
    }

    /**
     * Fetch full invoice details from metering service.
     * Uses JWT token for authentication.
     */
    private Map<String, Object> fetchInvoiceFromMeteringService(Long invoiceId, Long organizationId, String jwtToken) {
        try {
            log.info("📡 Fetching invoice {} from metering service at {}...", invoiceId, meteringServiceBaseUrl);
            
            if (jwtToken == null || jwtToken.isEmpty()) {
                log.error("❌ No JWT token provided, cannot authenticate with metering service");
                return null;
            }
            
            String invoiceUrl = meteringServiceBaseUrl + "/api/invoices/" + invoiceId;
            log.debug("Calling metering service: {}", invoiceUrl);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> invoice = webClient.get()
                    .uri(invoiceUrl)
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("X-Organization-Id", organizationId.toString())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("✅ Successfully fetched invoice {} from metering service", invoiceId);
            return invoice;
            
        } catch (Exception e) {
            log.error("❌ Failed to fetch invoice {} from metering service: {}", invoiceId, e.getMessage());
            return null;
        }
    }

    /**
     * Safely get Long value from map.
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
