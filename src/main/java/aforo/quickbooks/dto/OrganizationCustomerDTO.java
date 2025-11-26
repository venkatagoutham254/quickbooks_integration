package aforo.quickbooks.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO representing customer data from Organization Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationCustomerDTO {
    
    private Long customerId;
    private String customerName;
    private String companyName;
    private String companyType;
    private String primaryEmail;
    private String phoneNumber;
    private String status;  // DRAFT, ACTIVE
    
    // Address fields
    private String customerAddressLine1;
    private String customerAddressLine2;
    private String customerCity;
    private String customerState;
    private String customerPostalCode;
    private String customerCountry;
    
    // Billing address fields
    private Boolean billingSameAsCustomer;
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;
    
    /**
     * Get Aforo customer ID in standard format
     */
    public String getAforoId() {
        return "CUST-" + customerId;
    }
}
