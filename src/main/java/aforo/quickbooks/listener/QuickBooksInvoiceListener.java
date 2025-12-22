package aforo.quickbooks.listener;

import aforo.quickbooks.client.RatePlanServiceClient;
import aforo.quickbooks.dto.QuickBooksInvoiceRequest;
import aforo.quickbooks.entity.QuickBooksConnection;
import aforo.quickbooks.entity.QuickBooksMapping;
import aforo.quickbooks.mapper.InvoiceMapper;
import aforo.quickbooks.repository.QuickBooksConnectionRepository;
import aforo.quickbooks.service.QuickBooksApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Event listener that syncs invoices from metering service to QuickBooks.
 * Listens for InvoiceCreatedEvent and automatically creates the invoice in QuickBooks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuickBooksInvoiceListener {

    private final QuickBooksConnectionRepository connectionRepository;
    private final QuickBooksApiService apiService;
    private final InvoiceMapper invoiceMapper;
    private final WebClient webClient;
    private final RatePlanServiceClient ratePlanServiceClient;

    @Value("${aforo.metering-service.base-url}")
    private String meteringServiceBaseUrl;

    /**
     * Handle invoice created event from metering service.
     * This method receives the event as a Map to avoid circular dependencies
     * between services.
     *
     * Expected event structure:
     * {
     *   "invoiceId": 123,
     *   "organizationId": 2,
     *   "customerId": 456,
     *   "subscriptionId": 789,
     *   "ratePlanId": 1,
     *   "invoiceNumber": "INV-2-456-20251120",
     *   "totalAmount": 210.00,
     *   "billingPeriodStart": "2025-11-01T00:00:00Z",
     *   "billingPeriodEnd": "2025-11-30T23:59:59Z"
     * }
     */
    @EventListener(condition = "#event.class.simpleName == 'InvoiceCreatedEvent'")
    @Async
    public void handleInvoiceCreated(Object event) {
        try {
            // Convert event to map (avoiding direct dependency on metering service)
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = convertEventToMap(event);

            if (eventData == null) {
                log.warn("Received null or invalid invoice event");
                return;
            }

            Long invoiceId = getLong(eventData, "invoiceId");
            Long organizationId = getLong(eventData, "organizationId");
            Long customerId = getLong(eventData, "customerId");
            Long ratePlanId = getLong(eventData, "ratePlanId");
            String invoiceNumber = (String) eventData.get("invoiceNumber");
            String jwtToken = (String) eventData.get("jwtToken");

            log.info("Received InvoiceCreatedEvent for invoice {} (ID: {}) for organization {}",
                    invoiceNumber, invoiceId, organizationId);

            // Check if QuickBooks is connected for this organization
            Optional<QuickBooksConnection> connectionOpt = connectionRepository
                    .findByOrganizationId(organizationId);

            if (connectionOpt.isEmpty() || !connectionOpt.get().getIsActive()) {
                log.info("No active QuickBooks connection for organization {}. Skipping sync.", organizationId);
                return;
            }

            QuickBooksConnection connection = connectionOpt.get();

            // Get QuickBooks customer ID mapping
            // Use same format as OrganizationCustomerDTO.getAforoId() -> "CUST-{id}"
            String qbCustomerId = apiService.getQuickBooksId(
                    organizationId,
                    QuickBooksMapping.EntityType.CUSTOMER,
                    "CUST-" + customerId
            );

            if (qbCustomerId == null) {
                log.warn("Customer {} not synced to QuickBooks for organization {}. Cannot create invoice.",
                        customerId, organizationId);
                // TODO: Consider auto-syncing customer here or queuing for retry
                return;
            }

            // Fetch full invoice details from metering service
            Map<String, Object> invoiceData = fetchInvoiceFromMeteringService(invoiceId);
            if (invoiceData == null) {
                log.error("Failed to fetch invoice {} from metering service", invoiceId);
                return;
            }

            // Fetch rate plan name from rate plan service
            String ratePlanName = null;
            if (ratePlanId != null && jwtToken != null) {
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

            log.info("✅ Invoice {} synced to QuickBooks successfully. QB Invoice ID: {}",
                    invoiceNumber, qbInvoiceId);

        } catch (Exception e) {
            log.error("Failed to sync invoice to QuickBooks: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert event object to map using reflection.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertEventToMap(Object event) {
        if (event instanceof Map) {
            return (Map<String, Object>) event;
        }

        // Use reflection or JSON serialization to convert to map
        try {
            // Assuming event has getters - use simple reflection
            Class<?> eventClass = event.getClass();
            Map<String, Object> map = new java.util.HashMap<>();

            // Get all declared fields and extract values
            for (java.lang.reflect.Field field : eventClass.getDeclaredFields()) {
                field.setAccessible(true);
                map.put(field.getName(), field.get(event));
            }

            return map;
        } catch (Exception e) {
            log.error("Failed to convert event to map: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch full invoice details from metering service.
     */
    private Map<String, Object> fetchInvoiceFromMeteringService(Long invoiceId) {
        try {
            // Call metering service to get invoice with line items
            @SuppressWarnings("unchecked")
            Map<String, Object> invoice = webClient.get()
                    .uri(meteringServiceBaseUrl + "/api/invoices/{id}", invoiceId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return invoice;
        } catch (Exception e) {
            log.error("Failed to fetch invoice {} from metering service: {}", invoiceId, e.getMessage());
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
