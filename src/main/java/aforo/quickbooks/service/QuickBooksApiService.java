package aforo.quickbooks.service;

import aforo.quickbooks.config.QuickBooksApiConfig;
import aforo.quickbooks.dto.*;
import aforo.quickbooks.entity.QuickBooksConnection;
import aforo.quickbooks.entity.QuickBooksMapping;
import aforo.quickbooks.entity.QuickBooksSyncLog;
import aforo.quickbooks.exception.QuickBooksException;
import aforo.quickbooks.mapper.QuickBooksCustomerMapper;
import aforo.quickbooks.repository.QuickBooksMappingRepository;
import aforo.quickbooks.repository.QuickBooksSyncLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for QuickBooks API operations (customers, invoices, payments).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuickBooksApiService {

    private final QuickBooksOAuthService oauthService;
    private final QuickBooksApiConfig apiConfig;
    private final QuickBooksMappingRepository mappingRepository;
    private final QuickBooksSyncLogRepository syncLogRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final QuickBooksCustomerMapper customerMapper;

    /**
     * Create or update customer in QuickBooks.
     */
    @Retry(name = "quickbooks")
    @Transactional
    public String syncCustomer(Long organizationId, String aforoCustomerId, 
                               AforoCustomerRequest customerRequest) {
        QuickBooksConnection connection = oauthService.getActiveConnection(organizationId);
        
        long startTime = System.currentTimeMillis();
        QuickBooksSyncLog syncLog = QuickBooksSyncLog.builder()
            .organizationId(organizationId)
            .entityType(QuickBooksMapping.EntityType.CUSTOMER)
            .entityId(aforoCustomerId)
            .action(QuickBooksSyncLog.SyncAction.CREATE)
            .status(QuickBooksSyncLog.SyncStatus.PENDING)
            .requestData(toMap(customerRequest))
            .build();
        
        try {
            // Check if customer already exists in our mapping
            String qbCustomerId = mappingRepository
                .findByOrganizationIdAndEntityTypeAndAforoId(
                    organizationId, QuickBooksMapping.EntityType.CUSTOMER, aforoCustomerId)
                .map(QuickBooksMapping::getQuickbooksId)
                .orElse(null);
            
            String url = apiConfig.getResourceUrl(connection.getRealmId(), "customer");
            
            // Convert Aforo format to QuickBooks format
            ObjectNode qbCustomerJson = (ObjectNode) customerMapper.toQuickBooksFormat(customerRequest);
            String displayName = qbCustomerJson.get("DisplayName").asText();
            
            // If no mapping exists, search QuickBooks for existing customer by name
            if (qbCustomerId == null) {
                log.info("No mapping found, searching QuickBooks for customer with name: {}", displayName);
                qbCustomerId = searchCustomerByName(connection, displayName);
                
                if (qbCustomerId != null) {
                    log.info("Found existing customer in QuickBooks: {} ({})", displayName, qbCustomerId);
                    // Save the mapping we just discovered
                    saveMapping(organizationId, QuickBooksMapping.EntityType.CUSTOMER, 
                               aforoCustomerId, qbCustomerId);
                }
            }
            
            // If customer exists, fetch it to get SyncToken for update
            if (qbCustomerId != null) {
                log.info("Customer mapping exists, fetching from QuickBooks to update: {}", qbCustomerId);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> existingCustomer = webClient.get()
                    .uri(url + "/" + qbCustomerId)
                    .header("Authorization", "Bearer " + connection.getAccessToken())
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
                
                // Extract Customer object from response
                @SuppressWarnings("unchecked")
                Map<String, Object> customerData = (Map<String, Object>) existingCustomer.get("Customer");
                
                // Add Id and SyncToken for update
                qbCustomerJson.put("Id", qbCustomerId);
                qbCustomerJson.put("SyncToken", customerData.get("SyncToken").toString());
                
                syncLog.setAction(QuickBooksSyncLog.SyncAction.UPDATE);
                log.info("Updating existing QuickBooks customer: {}", qbCustomerId);
            } else {
                log.info("Creating new QuickBooks customer for: {}", aforoCustomerId);
            }
            
            // Log the JSON we're sending to QuickBooks for debugging
            log.info("Sending to QuickBooks: {}", objectMapper.writeValueAsString(qbCustomerJson));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(qbCustomerJson)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            qbCustomerId = extractId(response, "Customer");
            
            // Save mapping
            saveMapping(organizationId, QuickBooksMapping.EntityType.CUSTOMER, 
                       aforoCustomerId, qbCustomerId);
            
            // Log success
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.SUCCESS);
            syncLog.setResponseData(response);
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            
            log.info("Created/Updated QuickBooks customer {} for Aforo customer {}", 
                    qbCustomerId, aforoCustomerId);
            
            return qbCustomerId;
            
        } catch (WebClientResponseException e) {
            // Extract error details from QuickBooks response
            String errorBody = e.getResponseBodyAsString();
            log.error("QuickBooks API error: {} - Response: {}", e.getMessage(), errorBody);
            
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setErrorCode(String.valueOf(e.getStatusCode().value()));
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            
            // Try to parse error response
            try {
                Map errorResponse = objectMapper.readValue(errorBody, Map.class);
                syncLog.setResponseData(errorResponse);
            } catch (Exception parseEx) {
                log.warn("Failed to parse error response: {}", parseEx.getMessage());
            }
            
            syncLogRepository.save(syncLog);
            throw new QuickBooksException("Failed to sync customer: " + errorBody, e);
            
        } catch (Exception e) {
            log.error("Unexpected error syncing customer: {}", e.getMessage(), e);
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            
            throw new QuickBooksException("Failed to sync customer", e);
        }
    }

    /**
     * Create invoice in QuickBooks.
     */
    @Retry(name = "quickbooks")
    @Transactional
    public String createInvoice(Long organizationId, String aforoInvoiceId, 
                                QuickBooksInvoiceRequest invoiceRequest) {
        QuickBooksConnection connection = oauthService.getActiveConnection(organizationId);
        
        // Service items are already set correctly by the mapper (rate plan service item)
        // DO NOT override them here!
        
        long startTime = System.currentTimeMillis();
        QuickBooksSyncLog syncLog = QuickBooksSyncLog.builder()
            .organizationId(organizationId)
            .entityType(QuickBooksMapping.EntityType.INVOICE)
            .entityId(aforoInvoiceId)
            .action(QuickBooksSyncLog.SyncAction.CREATE)
            .status(QuickBooksSyncLog.SyncStatus.PENDING)
            .requestData(toMap(invoiceRequest))
            .build();
        
        try {
            String url = apiConfig.getResourceUrl(connection.getRealmId(), "invoice");
            
            // Log the request being sent to QuickBooks for debugging
            log.info("Creating invoice in QuickBooks. Request: {}", objectMapper.writeValueAsString(invoiceRequest));
            
            Map response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invoiceRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            String qbInvoiceId = extractId(response, "Invoice");
            
            // Save mapping
            saveMapping(organizationId, QuickBooksMapping.EntityType.INVOICE, 
                       aforoInvoiceId, qbInvoiceId);
            
            // Log success
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.SUCCESS);
            syncLog.setResponseData(response);
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            
            log.info("Created QuickBooks invoice {} for Aforo invoice {}", 
                    qbInvoiceId, aforoInvoiceId);
            
            return qbInvoiceId;
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("QuickBooks API error creating invoice: {} - Response: {}", e.getMessage(), errorBody);
            
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage() + " - " + errorBody);
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            
            throw new QuickBooksException("Failed to create invoice: " + errorBody, e);
        } catch (Exception e) {
            log.error("Unexpected error creating invoice: {}", e.getMessage(), e);
            
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            
            throw new QuickBooksException("Failed to create invoice", e);
        }
    }

    /**
     * Record payment in QuickBooks.
     */
    @Retry(name = "quickbooks")
    @Transactional
    public String recordPayment(Long organizationId, String aforoPaymentId, 
                                QuickBooksPaymentRequest paymentRequest) {
        QuickBooksConnection connection = oauthService.getActiveConnection(organizationId);
        
        long startTime = System.currentTimeMillis();
        QuickBooksSyncLog syncLog = QuickBooksSyncLog.builder()
            .organizationId(organizationId)
            .entityType(QuickBooksMapping.EntityType.PAYMENT)
            .entityId(aforoPaymentId)
            .action(QuickBooksSyncLog.SyncAction.CREATE)
            .status(QuickBooksSyncLog.SyncStatus.PENDING)
            .requestData(toMap(paymentRequest))
            .build();
        
        try {
            String url = apiConfig.getResourceUrl(connection.getRealmId(), "payment");
            
            Map response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paymentRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            String qbPaymentId = extractId(response, "Payment");
            
            // Save mapping
            saveMapping(organizationId, QuickBooksMapping.EntityType.PAYMENT, 
                       aforoPaymentId, qbPaymentId);
            
            // Log success
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.SUCCESS);
            syncLog.setResponseData(response);
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            
            log.info("Recorded QuickBooks payment {} for Aforo payment {}", 
                    qbPaymentId, aforoPaymentId);
            
            return qbPaymentId;
            
        } catch (Exception e) {
            syncLog.setStatus(QuickBooksSyncLog.SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            
            throw new QuickBooksException("Failed to record payment", e);
        }
    }

    /**
     * Get QuickBooks ID for an Aforo entity.
     */
    public String getQuickBooksId(Long organizationId, QuickBooksMapping.EntityType entityType, 
                                  String aforoId) {
        return mappingRepository
            .findByOrganizationIdAndEntityTypeAndAforoId(organizationId, entityType, aforoId)
            .map(QuickBooksMapping::getQuickbooksId)
            .orElse(null);
    }

    // Private helper methods
    
    private void saveMapping(Long organizationId, QuickBooksMapping.EntityType entityType,
                            String aforoId, String quickbooksId) {
        QuickBooksMapping mapping = mappingRepository
            .findByOrganizationIdAndEntityTypeAndAforoId(organizationId, entityType, aforoId)
            .orElse(new QuickBooksMapping());
        
        mapping.setOrganizationId(organizationId);
        mapping.setEntityType(entityType);
        mapping.setAforoId(aforoId);
        mapping.setQuickbooksId(quickbooksId);
        
        mappingRepository.save(mapping);
    }
    
    /**
     * Search for a customer in QuickBooks by display name
     * Retrieves all customers and filters by name in Java to avoid QuickBooks query API issues
     */
    /**
     * Get or create a service item in QuickBooks for invoice line items.
     * This searches for an existing "Services" item, or returns the first active service item found.
     */
    private String getOrCreateServiceItem(QuickBooksConnection connection) {
        try {
            log.info("Fetching service items from QuickBooks");
            
            // Fetch all items (QuickBooks query API has issues with WHERE, so fetch all and filter)
            String query = "SELECT * FROM Item MAXRESULTS 100";
            String url = apiConfig.getResourceUrl(connection.getRealmId(), "query") 
                       + "?query=" + java.net.URLEncoder.encode(query, "UTF-8");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response != null && response.containsKey("QueryResponse")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> queryResponse = (Map<String, Object>) response.get("QueryResponse");
                
                if (queryResponse.containsKey("Item")) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Map<String, Object>> items = 
                        (java.util.List<Map<String, Object>>) queryResponse.get("Item");
                    
                    // Filter for service items and active items in Java
                    java.util.List<Map<String, Object>> serviceItems = new java.util.ArrayList<>();
                    for (Map<String, Object> item : items) {
                        String type = (String) item.get("Type");
                        Boolean active = (Boolean) item.getOrDefault("Active", true);
                        
                        if ("Service".equals(type) && active) {
                            serviceItems.add(item);
                        }
                    }
                    
                    log.info("Found {} service items out of {} total items", serviceItems.size(), items.size());
                    
                    if (!serviceItems.isEmpty()) {
                        // Prefer "Services" item if it exists
                        for (Map<String, Object> item : serviceItems) {
                            String itemName = (String) item.get("Name");
                            if ("Services".equalsIgnoreCase(itemName)) {
                                String itemId = item.get("Id").toString();
                                log.info("Using existing 'Services' item: {}", itemId);
                                return itemId;
                            }
                        }
                        
                        // Otherwise, use the first service item
                        String itemId = serviceItems.get(0).get("Id").toString();
                        String itemName = (String) serviceItems.get(0).get("Name");
                        log.info("Using service item '{}' with ID: {}", itemName, itemId);
                        return itemId;
                    }
                }
            }
            
            throw new QuickBooksException("No service items found in QuickBooks. Please create at least one service item.");
            
        } catch (Exception e) {
            log.error("Failed to fetch service item: {}", e.getMessage(), e);
            throw new QuickBooksException("Failed to fetch service item from QuickBooks", e);
        }
    }
    
    /**
     * Find or create a service item in QuickBooks by name.
     * Searches for existing item first, creates new one if not found.
     * Automatically refreshes token if needed.
     * 
     * @param connection QuickBooks connection
     * @param itemName Name of the service item (e.g., "Flat Fee", "Overage Charges")
     * @return QuickBooks item ID
     */
    public String getOrCreateServiceItemByName(QuickBooksConnection connection, String itemName) {
        try {
            // Refresh connection token if expired
            connection = oauthService.getActiveConnection(connection.getOrganizationId());
            
            // Clean up item name (max 100 chars for QuickBooks)
            String cleanName = itemName != null ? itemName.trim() : "Service";
            if (cleanName.length() > 100) {
                cleanName = cleanName.substring(0, 100);
            }
            
            log.debug("Looking for service item: {}", cleanName);
            
            // Fetch all items to search
            String query = "SELECT * FROM Item MAXRESULTS 100";
            String url = apiConfig.getResourceUrl(connection.getRealmId(), "query") 
                       + "?query=" + java.net.URLEncoder.encode(query, "UTF-8");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            // Search for existing item with this name
            if (response != null && response.containsKey("QueryResponse")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> queryResponse = (Map<String, Object>) response.get("QueryResponse");
                
                if (queryResponse.containsKey("Item")) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Map<String, Object>> items = 
                        (java.util.List<Map<String, Object>>) queryResponse.get("Item");
                    
                    for (Map<String, Object> item : items) {
                        String type = (String) item.get("Type");
                        String name = (String) item.get("Name");
                        Boolean active = (Boolean) item.getOrDefault("Active", true);
                        
                        if ("Service".equals(type) && active && cleanName.equalsIgnoreCase(name)) {
                            String itemId = item.get("Id").toString();
                            log.debug("Found existing service item '{}': {}", cleanName, itemId);
                            return itemId;
                        }
                    }
                }
            }
            
            // Item not found - create new service item
            log.info("Service item '{}' not found, creating new item", cleanName);
            return createServiceItem(connection, cleanName);
            
        } catch (Exception e) {
            log.error("Failed to get/create service item '{}': {}", itemName, e.getMessage());
            // Fallback to default service item
            return getOrCreateServiceItem(connection);
        }
    }
    
    /**
     * Create a new service item in QuickBooks.
     */
    private String createServiceItem(QuickBooksConnection connection, String itemName) {
        try {
            Map<String, Object> itemRequest = new java.util.HashMap<>();
            itemRequest.put("Name", itemName);
            itemRequest.put("Type", "Service");
            itemRequest.put("Active", true);
            
            // Create IncomeAccountRef - use default income account
            Map<String, String> incomeAccountRef = new java.util.HashMap<>();
            incomeAccountRef.put("value", "79"); // Sales of Product Income (default QuickBooks account)
            itemRequest.put("IncomeAccountRef", incomeAccountRef);
            
            String url = apiConfig.getResourceUrl(connection.getRealmId(), "item");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(itemRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response != null && response.containsKey("Item")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) response.get("Item");
                String itemId = item.get("Id").toString();
                log.info("✅ Created new service item '{}': {}", itemName, itemId);
                return itemId;
            }
            
            throw new QuickBooksException("Failed to create service item: No Item in response");
            
        } catch (Exception e) {
            log.error("Failed to create service item '{}': {}", itemName, e.getMessage(), e);
            throw new QuickBooksException("Failed to create service item", e);
        }
    }
    
    private String searchCustomerByName(QuickBooksConnection connection, String displayName) {
        try {
            log.info("Searching for customer '{}' in QuickBooks by fetching all customers", displayName);
            
            // Fetch all customers (QuickBooks query API has issues, so we fetch all and filter)
            String url = apiConfig.getResourceUrl(connection.getRealmId(), "query") 
                       + "?query=" + java.net.URLEncoder.encode("SELECT * FROM Customer MAXRESULTS 1000", "UTF-8");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response != null && response.containsKey("QueryResponse")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> queryResponse = (Map<String, Object>) response.get("QueryResponse");
                
                if (queryResponse.containsKey("Customer")) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Map<String, Object>> customers = 
                        (java.util.List<Map<String, Object>>) queryResponse.get("Customer");
                    
                    // Filter by display name in Java
                    for (Map<String, Object> customer : customers) {
                        String customerDisplayName = (String) customer.get("DisplayName");
                        if (displayName.equalsIgnoreCase(customerDisplayName)) {
                            String customerId = customer.get("Id").toString();
                            log.info("Found matching customer in QuickBooks: {} -> ID: {}", displayName, customerId);
                            return customerId;
                        }
                    }
                    
                    log.info("No customer found with display name: {} (searched {} customers)", 
                            displayName, customers.size());
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Failed to search for customer by name '{}': {}", displayName, e.getMessage(), e);
            return null;
        }
    }

    private String extractId(Map response, String entityKey) {
        if (response != null && response.containsKey(entityKey)) {
            Map entity = (Map) response.get(entityKey);
            return entity.get("Id").toString();
        }
        throw new QuickBooksException("Failed to extract ID from QuickBooks response");
    }

    private Map<String, Object> toMap(Object object) {
        return objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
    }
}
