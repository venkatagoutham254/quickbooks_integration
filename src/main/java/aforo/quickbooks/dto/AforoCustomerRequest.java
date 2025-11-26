package aforo.quickbooks.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * DTO representing Aforo's customer format (from organization service)
 * This matches the exact structure of CreateCustomerRequest from organization service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AforoCustomerRequest {
    
    // Basic Info
    private String companyName;
    private String customerName;
    private String companyType;  // INDIVIDUAL / BUSINESS
    private String phoneNumber;
    private String primaryEmail;
    
    // Additional Email Recipients
    private List<String> additionalEmailRecipients;
    
    // Customer Address (flat structure)
    private String customerAddressLine1;
    private String customerAddressLine2;
    private String customerCity;
    private String customerState;
    private String customerPostalCode;
    private String customerCountry;
    
    // Billing Address (flat structure)
    private Boolean billingSameAsCustomer;
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;
}
