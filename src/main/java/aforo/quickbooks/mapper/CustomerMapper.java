package aforo.quickbooks.mapper;

import aforo.quickbooks.dto.AforoCustomerRequest;
import aforo.quickbooks.dto.OrganizationCustomerDTO;
import org.springframework.stereotype.Component;

/**
 * Maps between Organization Service customer DTOs and Aforo customer requests
 */
@Component
public class CustomerMapper {

    /**
     * Convert Organization Service customer DTO to Aforo customer request format
     * 
     * @param orgCustomer Customer from organization service
     * @return AforoCustomerRequest ready for QuickBooks sync
     */
    public AforoCustomerRequest toAforoCustomerRequest(OrganizationCustomerDTO orgCustomer) {
        return AforoCustomerRequest.builder()
                .companyName(orgCustomer.getCompanyName())
                .customerName(orgCustomer.getCustomerName())
                .companyType(orgCustomer.getCompanyType())
                .phoneNumber(orgCustomer.getPhoneNumber())
                .primaryEmail(orgCustomer.getPrimaryEmail())
                
                // Customer address
                .customerAddressLine1(orgCustomer.getCustomerAddressLine1())
                .customerAddressLine2(orgCustomer.getCustomerAddressLine2())
                .customerCity(orgCustomer.getCustomerCity())
                .customerState(orgCustomer.getCustomerState())
                .customerPostalCode(orgCustomer.getCustomerPostalCode())
                .customerCountry(orgCustomer.getCustomerCountry())
                
                // Billing address
                .billingSameAsCustomer(orgCustomer.getBillingSameAsCustomer())
                .billingAddressLine1(orgCustomer.getBillingAddressLine1())
                .billingAddressLine2(orgCustomer.getBillingAddressLine2())
                .billingCity(orgCustomer.getBillingCity())
                .billingState(orgCustomer.getBillingState())
                .billingPostalCode(orgCustomer.getBillingPostalCode())
                .billingCountry(orgCustomer.getBillingCountry())
                
                .build();
    }
}
