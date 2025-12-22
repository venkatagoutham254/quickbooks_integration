package aforo.quickbooks.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Client for fetching rate plan details from the rate plan service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RatePlanServiceClient {

    private final WebClient webClient;

    @Value("${aforo.rate-plan-service.base-url}")
    private String ratePlanServiceBaseUrl;

    /**
     * Fetch rate plan details by ID.
     *
     * @param ratePlanId Rate plan ID
     * @param jwtToken JWT token for authentication
     * @return Rate plan name, or null if not found
     */
    public String getRatePlanName(Long ratePlanId, String jwtToken) {
        try {
            log.debug("Fetching rate plan {} from rate plan service", ratePlanId);

            @SuppressWarnings("unchecked")
            Map<String, Object> ratePlan = webClient.get()
                    .uri(ratePlanServiceBaseUrl + "/api/rateplans/" + ratePlanId)
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (ratePlan != null && ratePlan.containsKey("ratePlanName")) {
                String ratePlanName = (String) ratePlan.get("ratePlanName");
                log.debug("Found rate plan name: {}", ratePlanName);
                return ratePlanName;
            }

            log.warn("Rate plan {} not found or has no name", ratePlanId);
            return null;

        } catch (Exception e) {
            log.error("Failed to fetch rate plan {}: {}", ratePlanId, e.getMessage(), e);
            return null;
        }
    }
}
