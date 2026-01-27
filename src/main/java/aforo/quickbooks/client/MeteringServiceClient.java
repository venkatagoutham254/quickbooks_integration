package aforo.quickbooks.client;

import aforo.quickbooks.dto.MeteringInvoiceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client for calling Metering Service APIs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeteringServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${aforo.metering-service.base-url}")
    private String meteringServiceBaseUrl;

    @Value("${aforo.metering-service.timeout-seconds:30}")
    private int timeoutSeconds;

    /**
     * Get all invoices from metering service for an organization
     *
     * @param authToken JWT token for authentication
     * @param organizationId Organization ID
     * @return List of invoices
     */
    public List<MeteringInvoiceDTO> getAllInvoices(String authToken, Long organizationId) {
        log.info("📞 Fetching all invoices from metering service for org {}", organizationId);

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(meteringServiceBaseUrl)
                    .defaultHeader("Authorization", authToken)
                    .defaultHeader("X-Organization-Id", organizationId.toString())
                    .build();

            List<Map<String, Object>> invoiceData = webClient.get()
                    .uri("/api/invoices")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (invoiceData != null && !invoiceData.isEmpty()) {
                List<MeteringInvoiceDTO> invoices = invoiceData.stream()
                        .map(this::mapToInvoiceDTO)
                        .toList();
                
                log.info("✅ Retrieved {} invoices from metering service", invoices.size());
                return invoices;
            }

            log.info("✅ No invoices found in metering service");
            return List.of();

        } catch (Exception e) {
            log.error("❌ Failed to fetch invoices from metering service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch invoices from metering service", e);
        }
    }

    /**
     * Get invoices with specific status from metering service
     *
     * @param authToken JWT token for authentication
     * @param organizationId Organization ID
     * @param status Invoice status (FINALIZED, PAID, etc.)
     * @return List of invoices with the specified status
     */
    public List<MeteringInvoiceDTO> getInvoicesByStatus(String authToken, Long organizationId, String status) {
        log.info("📞 Fetching {} invoices from metering service for org {}", status, organizationId);

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(meteringServiceBaseUrl)
                    .defaultHeader("Authorization", authToken)
                    .defaultHeader("X-Organization-Id", organizationId.toString())
                    .build();

            List<Map<String, Object>> invoiceData = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/invoices")
                            .queryParam("status", status)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (invoiceData != null && !invoiceData.isEmpty()) {
                List<MeteringInvoiceDTO> invoices = invoiceData.stream()
                        .map(this::mapToInvoiceDTO)
                        .toList();
                
                log.info("✅ Retrieved {} {} invoices from metering service", invoices.size(), status);
                return invoices;
            }

            log.info("✅ No {} invoices found in metering service", status);
            return List.of();

        } catch (Exception e) {
            log.error("❌ Failed to fetch {} invoices from metering service: {}", status, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch invoices from metering service", e);
        }
    }

    /**
     * Map response data to InvoiceDTO
     */
    private MeteringInvoiceDTO mapToInvoiceDTO(Map<String, Object> data) {
        MeteringInvoiceDTO dto = new MeteringInvoiceDTO();
        
        dto.setInvoiceId(getLong(data, "invoiceId"));
        dto.setOrganizationId(getLong(data, "organizationId"));
        dto.setCustomerId(getLong(data, "customerId"));
        dto.setSubscriptionId(getLong(data, "subscriptionId"));
        dto.setRatePlanId(getLong(data, "ratePlanId"));
        dto.setInvoiceNumber(getString(data, "invoiceNumber"));
        dto.setTotalAmount(getBigDecimal(data, "totalAmount"));
        dto.setStatus(getString(data, "status"));
        dto.setBillingPeriodStart(getInstant(data, "billingPeriodStart"));
        dto.setBillingPeriodEnd(getInstant(data, "billingPeriodEnd"));
        dto.setCreatedAt(getInstant(data, "createdAt"));
        dto.setUpdatedAt(getInstant(data, "updatedAt"));
        
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

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Instant getInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
