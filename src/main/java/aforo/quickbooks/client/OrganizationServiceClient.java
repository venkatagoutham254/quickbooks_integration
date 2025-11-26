package aforo.quickbooks.client;

import aforo.quickbooks.config.OrganizationServiceProperties;
import aforo.quickbooks.dto.OrganizationCustomerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for calling Organization Service APIs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationServiceClient {

    private final OrganizationServiceProperties properties;
    private final WebClient.Builder webClientBuilder;

    /**
     * Get all ACTIVE customers from organization service
     *
     * @param authToken JWT token for authentication
     * @param organizationId Organization ID
     * @return List of active customers
     */
    public List<OrganizationCustomerDTO> getActiveCustomers(String authToken, Long organizationId) {
        log.info("📞 Fetching active customers from organization service for org {}", organizationId);

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader("Authorization", authToken)
                    .build();

            // Organization service returns a plain array, not a paginated response
            List<Map<String, Object>> customerData = webClient.get()
                    .uri("/v1/api/customers?status=ACTIVE")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();

            if (customerData != null && !customerData.isEmpty()) {
                List<OrganizationCustomerDTO> customers = customerData.stream()
                        .map(this::mapToCustomerDTO)
                        .toList();
                
                log.info("✅ Retrieved {} active customers from organization service", customers.size());
                return customers;
            }

            log.info("✅ No active customers found in organization service");
            return List.of();

        } catch (Exception e) {
            log.error("❌ Failed to fetch customers from organization service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch customers from organization service", e);
        }
    }

    /**
     * Get a specific customer by ID
     *
     * @param authToken JWT token
     * @param customerId Customer ID
     * @return Customer DTO
     */
    public OrganizationCustomerDTO getCustomer(String authToken, Long customerId) {
        log.info("📞 Fetching customer {} from organization service", customerId);

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader("Authorization", authToken)
                    .build();

            Map<String, Object> customerData = webClient.get()
                    .uri("/v1/api/customers/{id}", customerId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();

            if (customerData != null) {
                OrganizationCustomerDTO customer = mapToCustomerDTO(customerData);
                log.info("✅ Retrieved customer: {}", customer.getCustomerName());
                return customer;
            }

            throw new RuntimeException("Customer not found: " + customerId);

        } catch (Exception e) {
            log.error("❌ Failed to fetch customer {}: {}", customerId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch customer from organization service", e);
        }
    }

    /**
     * Map response data to CustomerDTO
     */
    private OrganizationCustomerDTO mapToCustomerDTO(Map<String, Object> data) {
        OrganizationCustomerDTO dto = new OrganizationCustomerDTO();
        
        dto.setCustomerId(getLong(data, "customerId"));
        dto.setCustomerName(getString(data, "customerName"));
        dto.setCompanyName(getString(data, "companyName"));
        dto.setCompanyType(getString(data, "companyType"));
        dto.setPrimaryEmail(getString(data, "primaryEmail"));
        dto.setPhoneNumber(getString(data, "phoneNumber"));
        dto.setStatus(getString(data, "status"));
        
        // Address fields
        dto.setCustomerAddressLine1(getString(data, "customerAddressLine1"));
        dto.setCustomerAddressLine2(getString(data, "customerAddressLine2"));
        dto.setCustomerCity(getString(data, "customerCity"));
        dto.setCustomerState(getString(data, "customerState"));
        dto.setCustomerPostalCode(getString(data, "customerPostalCode"));
        dto.setCustomerCountry(getString(data, "customerCountry"));
        
        // Billing address
        dto.setBillingSameAsCustomer(getBoolean(data, "billingSameAsCustomer"));
        dto.setBillingAddressLine1(getString(data, "billingAddressLine1"));
        dto.setBillingAddressLine2(getString(data, "billingAddressLine2"));
        dto.setBillingCity(getString(data, "billingCity"));
        dto.setBillingState(getString(data, "billingState"));
        dto.setBillingPostalCode(getString(data, "billingPostalCode"));
        dto.setBillingCountry(getString(data, "billingCountry"));
        
        return dto;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
}
