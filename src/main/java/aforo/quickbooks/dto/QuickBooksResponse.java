package aforo.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Generic DTO for QuickBooks API responses.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickBooksResponse<T> {

    @JsonProperty("Customer")
    private T customer;

    @JsonProperty("Invoice")
    private T invoice;

    @JsonProperty("Payment")
    private T payment;

    @JsonProperty("time")
    private String time;

    @JsonProperty("Fault")
    private Fault fault;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fault {
        @JsonProperty("Error")
        private Error[] error;
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {
        private String Message;
        private String Detail;
        private String code;
        private String element;
    }

    /**
     * Extract the entity from the response based on type.
     */
    public T getEntity() {
        if (customer != null) return customer;
        if (invoice != null) return invoice;
        if (payment != null) return payment;
        return null;
    }

    /**
     * Check if response has errors.
     */
    public boolean hasErrors() {
        return fault != null && fault.error != null && fault.error.length > 0;
    }

    /**
     * Get error message if present.
     */
    public String getErrorMessage() {
        if (hasErrors()) {
            return fault.error[0].getMessage();
        }
        return null;
    }
}
