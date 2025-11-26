package aforo.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating/updating QuickBooks customer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuickBooksCustomerRequest {

    private String DisplayName;
    private EmailAddress PrimaryEmailAddr;
    private String CompanyName;
    private PhysicalAddress BillAddr;
    private PhysicalAddress ShipAddr;
    private String PrimaryPhone;
    private String Notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmailAddress {
        private String Address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhysicalAddress {
        private String Line1;
        private String Line2;
        private String City;
        private String Country;
        private String PostalCode;
        private String CountrySubDivisionCode;  // State/Province
    }
}
