package aforo.quickbooks.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO representing invoice data from Metering Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeteringInvoiceDTO {
    
    private Long invoiceId;
    private Long organizationId;
    private Long customerId;
    private Long subscriptionId;
    private Long ratePlanId;
    private String invoiceNumber;
    private BigDecimal totalAmount;
    private String status;  // DRAFT, FINALIZED, PAID, CANCELLED
    private Instant billingPeriodStart;
    private Instant billingPeriodEnd;
    private Instant createdAt;
    private Instant updatedAt;
    
    /**
     * Get Aforo invoice ID in standard format
     */
    public String getAforoId() {
        return "invoice-" + invoiceId;
    }
}
