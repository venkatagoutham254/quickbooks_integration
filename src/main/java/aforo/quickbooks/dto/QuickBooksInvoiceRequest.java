package aforo.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for creating/updating QuickBooks invoice.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuickBooksInvoiceRequest {

    @JsonProperty("CustomerRef")
    private ReferenceType CustomerRef;
    
    @JsonProperty("DocNumber")
    private String DocNumber;
    
    @JsonProperty("TxnDate")
    private String TxnDate;
    
    @JsonProperty("DueDate")
    private String DueDate;
    
    @JsonProperty("Line")
    private List<Line> Line;
    
    @JsonProperty("PrivateNote")
    private String PrivateNote;
    
    @JsonProperty("CustomerMemo")
    private MemoRef CustomerMemo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReferenceType {
        @JsonProperty("value")
        private String value;
        
        @JsonProperty("name")
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MemoRef {
        @JsonProperty("value")
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Line {
        @JsonProperty("Id")
        private String Id;
        
        @JsonProperty("Amount")
        private BigDecimal Amount;
        
        @JsonProperty("Description")
        private String Description;
        
        @JsonProperty("DetailType")
        private String DetailType;
        
        @JsonProperty("SalesItemLineDetail")
        private SalesItemLineDetail SalesItemLineDetail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SalesItemLineDetail {
        @JsonProperty("ItemRef")
        private ReferenceType ItemRef;
        
        @JsonProperty("UnitPrice")
        private BigDecimal UnitPrice;
        
        @JsonProperty("Qty")
        private Integer Qty;
    }
}
