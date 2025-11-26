package aforo.quickbooks.mapper;

import aforo.quickbooks.dto.AforoCustomerRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Maps Aforo customer format to QuickBooks API format
 */
@Component
public class QuickBooksCustomerMapper {
    
    private final ObjectMapper objectMapper;
    
    public QuickBooksCustomerMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Converts Aforo customer request to QuickBooks Customer JSON format
     * 
     * @param aforoCustomer Customer data in Aforo format (flat structure)
     * @return JsonNode in QuickBooks API format
     */
    public JsonNode toQuickBooksFormat(AforoCustomerRequest aforoCustomer) {
        ObjectNode qbCustomer = objectMapper.createObjectNode();
        
        // Display Name (required by QuickBooks)
        String displayName = aforoCustomer.getCustomerName() != null 
            ? aforoCustomer.getCustomerName() 
            : aforoCustomer.getCompanyName();
        qbCustomer.put("DisplayName", displayName);
        
        // Company Name
        if (aforoCustomer.getCompanyName() != null) {
            qbCustomer.put("CompanyName", aforoCustomer.getCompanyName());
        }
        
        // Primary Email
        if (aforoCustomer.getPrimaryEmail() != null) {
            ObjectNode emailAddr = objectMapper.createObjectNode();
            emailAddr.put("Address", aforoCustomer.getPrimaryEmail());
            qbCustomer.set("PrimaryEmailAddr", emailAddr);
        }
        
        // Primary Phone
        if (aforoCustomer.getPhoneNumber() != null) {
            ObjectNode phone = objectMapper.createObjectNode();
            phone.put("FreeFormNumber", aforoCustomer.getPhoneNumber());
            qbCustomer.set("PrimaryPhone", phone);
        }
        
        // Billing Address (QuickBooks uses this as primary address)
        // If billingSameAsCustomer = true, use customer address for billing
        boolean useSameAddress = aforoCustomer.getBillingSameAsCustomer() != null 
                && aforoCustomer.getBillingSameAsCustomer();
        
        String billLine1 = useSameAddress ? aforoCustomer.getCustomerAddressLine1() : aforoCustomer.getBillingAddressLine1();
        String billLine2 = useSameAddress ? aforoCustomer.getCustomerAddressLine2() : aforoCustomer.getBillingAddressLine2();
        String billCity = useSameAddress ? aforoCustomer.getCustomerCity() : aforoCustomer.getBillingCity();
        String billState = useSameAddress ? aforoCustomer.getCustomerState() : aforoCustomer.getBillingState();
        String billPostal = useSameAddress ? aforoCustomer.getCustomerPostalCode() : aforoCustomer.getBillingPostalCode();
        String billCountry = useSameAddress ? aforoCustomer.getCustomerCountry() : aforoCustomer.getBillingCountry();
        
        if (billLine1 != null || billCity != null || billState != null) {
            ObjectNode billAddr = objectMapper.createObjectNode();
            
            if (billLine1 != null) {
                billAddr.put("Line1", billLine1);
            }
            if (billLine2 != null) {
                billAddr.put("Line2", billLine2);
            }
            if (billCity != null) {
                billAddr.put("City", billCity);
            }
            if (billState != null) {
                billAddr.put("CountrySubDivisionCode", billState);
            }
            if (billPostal != null) {
                billAddr.put("PostalCode", billPostal);
            }
            if (billCountry != null) {
                billAddr.put("Country", billCountry);
            }
            
            qbCustomer.set("BillAddr", billAddr);
        }
        
        // Shipping Address (if different from billing)
        if (!useSameAddress) {
            String shipLine1 = aforoCustomer.getCustomerAddressLine1();
            String shipLine2 = aforoCustomer.getCustomerAddressLine2();
            String shipCity = aforoCustomer.getCustomerCity();
            String shipState = aforoCustomer.getCustomerState();
            String shipPostal = aforoCustomer.getCustomerPostalCode();
            String shipCountry = aforoCustomer.getCustomerCountry();
            
            if (shipLine1 != null || shipCity != null || shipState != null) {
                ObjectNode shipAddress = objectMapper.createObjectNode();
                
                if (shipLine1 != null) {
                    shipAddress.put("Line1", shipLine1);
                }
                if (shipLine2 != null) {
                    shipAddress.put("Line2", shipLine2);
                }
                if (shipCity != null) {
                    shipAddress.put("City", shipCity);
                }
                if (shipState != null) {
                    shipAddress.put("CountrySubDivisionCode", shipState);
                }
                if (shipPostal != null) {
                    shipAddress.put("PostalCode", shipPostal);
                }
                if (shipCountry != null) {
                    shipAddress.put("Country", shipCountry);
                }
                
                qbCustomer.set("ShipAddr", shipAddress);
            }
        }
        
        return qbCustomer;
    }
}
