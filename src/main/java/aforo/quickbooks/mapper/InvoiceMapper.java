package aforo.quickbooks.mapper;

import aforo.quickbooks.dto.QuickBooksInvoiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper to convert metering service Invoice entities to QuickBooks invoice format.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Map a metering invoice (with line items) to QuickBooks invoice format.
     *
     * @param invoiceData Invoice data from metering service (as map to avoid dependency)
     * @param qbCustomerId QuickBooks customer ID (already mapped)
     * @param qbServiceItemId QuickBooks service item ID (rate plan service item)
     * @return QuickBooks invoice request
     */
    public QuickBooksInvoiceRequest toQuickBooksFormat(
            Map<String, Object> invoiceData,
            String qbCustomerId,
            String qbServiceItemId
    ) {
        log.debug("Mapping invoice to QuickBooks format");

        // Extract invoice fields
        String invoiceNumber = (String) invoiceData.get("invoiceNumber");
        BigDecimal totalAmount = convertToBigDecimal(invoiceData.get("totalAmount"));
        Instant billingPeriodStart = convertToInstant(invoiceData.get("billingPeriodStart"));
        Instant billingPeriodEnd = convertToInstant(invoiceData.get("billingPeriodEnd"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) invoiceData.get("lineItems");

        // Convert dates to LocalDate for QuickBooks
        LocalDate txnDate = billingPeriodEnd != null ?
                billingPeriodEnd.atZone(ZoneId.of("UTC")).toLocalDate() :
                LocalDate.now();
        
        LocalDate dueDate = txnDate.plusDays(30); // Net 30 terms

        // Build customer reference
        QuickBooksInvoiceRequest.ReferenceType customerRef =
                QuickBooksInvoiceRequest.ReferenceType.builder()
                        .value(qbCustomerId)
                        .build();

        // Build line items
        List<QuickBooksInvoiceRequest.Line> qbLines = new ArrayList<>();

        if (lineItems != null && !lineItems.isEmpty()) {
            for (Map<String, Object> lineItem : lineItems) {
                String description = (String) lineItem.get("description");
                String calculation = (String) lineItem.get("calculation");
                BigDecimal amount = convertToBigDecimal(lineItem.get("amount"));

                // Combine description and calculation for full line description
                String fullDescription = description;
                if (calculation != null && !calculation.isEmpty() && !calculation.equals(description)) {
                    fullDescription = description + " (" + calculation + ")";
                }

                // Create sales item line detail - use rate plan service item for ALL lines
                QuickBooksInvoiceRequest.SalesItemLineDetail salesDetail =
                        QuickBooksInvoiceRequest.SalesItemLineDetail.builder()
                                .ItemRef(QuickBooksInvoiceRequest.ReferenceType.builder()
                                        .value(qbServiceItemId)
                                        .build())
                                .Qty(1)  // Each line represents one item
                                .UnitPrice(amount != null ? amount : BigDecimal.ZERO)
                                .build();

                // Create line
                QuickBooksInvoiceRequest.Line line =
                        QuickBooksInvoiceRequest.Line.builder()
                                .Amount(amount != null ? amount : BigDecimal.ZERO)
                                .Description(fullDescription)
                                .DetailType("SalesItemLineDetail")
                                .SalesItemLineDetail(salesDetail)
                                .build();

                qbLines.add(line);
            }
        } else {
            // If no line items, create a single line for the total
            QuickBooksInvoiceRequest.SalesItemLineDetail salesDetail =
                    QuickBooksInvoiceRequest.SalesItemLineDetail.builder()
                            .ItemRef(QuickBooksInvoiceRequest.ReferenceType.builder()
                                    .value(qbServiceItemId)
                                    .build())
                            .Qty(1)
                            .UnitPrice(totalAmount != null ? totalAmount : BigDecimal.ZERO)
                            .build();

            QuickBooksInvoiceRequest.Line line =
                    QuickBooksInvoiceRequest.Line.builder()
                            .Amount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
                            .Description("Usage charges")
                            .DetailType("SalesItemLineDetail")
                            .SalesItemLineDetail(salesDetail)
                            .build();

            qbLines.add(line);
        }

        // Build customer memo reference
        QuickBooksInvoiceRequest.MemoRef customerMemo = QuickBooksInvoiceRequest.MemoRef.builder()
                .value("Usage charges for billing period: " +
                        formatInstant(billingPeriodStart) + " to " + formatInstant(billingPeriodEnd))
                .build();

        // Build the invoice request
        QuickBooksInvoiceRequest invoice = QuickBooksInvoiceRequest.builder()
                .CustomerRef(customerRef)
                .DocNumber(invoiceNumber)
                .TxnDate(txnDate.format(DATE_FORMATTER))
                .DueDate(dueDate.format(DATE_FORMATTER))
                .Line(qbLines)
                .PrivateNote("Auto-generated from metering service")
                .CustomerMemo(customerMemo)
                .build();

        log.debug("Mapped invoice {} with {} line items", invoiceNumber, qbLines.size());

        return invoice;
    }

    /**
     * Convert object to BigDecimal safely.
     */
    private BigDecimal convertToBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to convert {} to BigDecimal", value);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Convert object to Instant safely.
     */
    private Instant convertToInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception e) {
                log.warn("Failed to parse instant from string: {}", value);
                return null;
            }
        }
        return null;
    }

    /**
     * Format Instant for display.
     */
    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return instant.atZone(ZoneId.of("UTC")).toLocalDate().toString();
    }
}
