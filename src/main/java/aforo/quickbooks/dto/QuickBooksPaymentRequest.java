package aforo.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for creating QuickBooks payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuickBooksPaymentRequest {

    private QuickBooksInvoiceRequest.ReferenceType CustomerRef;
    private BigDecimal TotalAmt;
    private String TxnDate;
    private List<Line> Line;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Line {
        private BigDecimal Amount;
        private List<LinkedTxn> LinkedTxn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkedTxn {
        private String TxnId;
        private String TxnType;
    }
}
