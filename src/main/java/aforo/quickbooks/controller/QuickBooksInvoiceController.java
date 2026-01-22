package aforo.quickbooks.controller;

import aforo.quickbooks.entity.QuickBooksMapping;
import aforo.quickbooks.repository.QuickBooksMappingRepository;
import aforo.quickbooks.security.TenantContext;
import aforo.quickbooks.service.QuickBooksApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

/**
 * Controller for QuickBooks invoice retrieval and PDF generation.
 * Provides endpoints to fetch invoices and their PDFs from QuickBooks.
 */
@RestController
@RequestMapping("/api/quickbooks/invoices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QuickBooks Invoices", description = "Endpoints for fetching invoices and PDFs from QuickBooks")
public class QuickBooksInvoiceController {

    private final QuickBooksApiService apiService;
    private final QuickBooksMappingRepository mappingRepository;

    /**
     * Get list of invoices from QuickBooks.
     * Returns ALL invoices for the organization (automatically handles pagination internally).
     * Organization ID is automatically extracted from JWT token.
     * 
     * Example: GET /api/quickbooks/invoices
     */
    @GetMapping
    @Operation(
        summary = "Get all invoices from QuickBooks",
        description = "Fetch all invoices from QuickBooks. Organization ID automatically extracted from JWT token. Pagination is handled internally."
    )
    public ResponseEntity<Map<String, Object>> getInvoices() {
        
        Long organizationId = TenantContext.require();
        log.info("Fetching all invoices for organization: {}", organizationId);
        
        try {
            Map<String, Object> result = apiService.getAllInvoices(organizationId);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to fetch invoices: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to fetch invoices from QuickBooks: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Get single invoice details from QuickBooks.
     * Returns full invoice data including line items.
     * Organization ID is automatically extracted from JWT token.
     * 
     * Example: GET /api/quickbooks/invoices/163
     */
    @GetMapping("/{invoiceId}")
    @Operation(
        summary = "Get invoice details",
        description = "Fetch detailed invoice information from QuickBooks including line items. Organization ID automatically extracted from JWT token."
    )
    public ResponseEntity<Map<String, Object>> getInvoice(
            @Parameter(description = "QuickBooks invoice ID", required = true)
            @PathVariable String invoiceId) {
        
        Long organizationId = TenantContext.require();
        log.info("Fetching invoice {} for organization: {}", invoiceId, organizationId);
        
        try {
            // Security check: verify this invoice belongs to the organization
            verifyInvoiceOwnership(organizationId, invoiceId);
            
            Map<String, Object> invoice = apiService.getSingleInvoice(organizationId, invoiceId);
            return ResponseEntity.ok(invoice);
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch invoice {}: {}", invoiceId, e.getMessage(), e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to fetch invoice from QuickBooks: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Get invoice PDF from QuickBooks.
     * Returns the PDF file that can be viewed or downloaded.
     * Organization ID can be from JWT token or query parameter (for browser access).
     * 
     * Example: GET /api/quickbooks/invoices/163/pdf?organizationId=2&download=true
     * 
     * This will return the PDF with proper headers for inline viewing or download.
     */
    @GetMapping("/{invoiceId}/pdf")
    @Operation(
        summary = "Get invoice PDF",
        description = "Download or view invoice PDF from QuickBooks. Organization ID from JWT token or query parameter."
    )
    public ResponseEntity<byte[]> getInvoicePdf(
            @Parameter(description = "QuickBooks invoice ID", required = true)
            @PathVariable String invoiceId,
            
            @Parameter(description = "Organization ID (optional if JWT provided)")
            @RequestParam(required = false) Long organizationId,
            
            @Parameter(description = "Download as file (default: false = inline view)")
            @RequestParam(required = false, defaultValue = "false") boolean download) {
        
        // Try JWT first, fallback to query parameter
        if (organizationId == null) {
            if (TenantContext.isSet()) {
                organizationId = TenantContext.getOrganizationId();
            } else {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization ID required: provide JWT token or organizationId parameter"
                );
            }
        }
        
        log.info("Fetching PDF for invoice {} (organization: {}, download: {})", 
                invoiceId, organizationId, download);
        
        try {
            // Security check: verify this invoice belongs to the organization
            verifyInvoiceOwnership(organizationId, invoiceId);
            
            // Fetch PDF from QuickBooks
            byte[] pdfBytes = apiService.getInvoicePdf(organizationId, invoiceId);
            
            // Build response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            
            // Set content disposition based on download parameter
            ContentDisposition.Builder dispositionBuilder = download 
                ? ContentDisposition.attachment() 
                : ContentDisposition.inline();
            
            headers.setContentDisposition(
                dispositionBuilder
                    .filename("invoice-" + invoiceId + ".pdf")
                    .build()
            );
            
            headers.setContentLength(pdfBytes.length);
            
            log.info("Returning PDF for invoice {}: {} bytes", invoiceId, pdfBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch PDF for invoice {}: {}", invoiceId, e.getMessage(), e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to fetch invoice PDF from QuickBooks: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Security check: Verify that the invoice belongs to the organization.
     * Checks the mapping table to ensure the organization has access to this invoice.
     */
    private void verifyInvoiceOwnership(Long organizationId, String quickbooksInvoiceId) {
        Optional<QuickBooksMapping> mapping = mappingRepository
            .findByOrganizationIdAndEntityTypeAndQuickbooksId(
                organizationId,
                QuickBooksMapping.EntityType.INVOICE,
                quickbooksInvoiceId
            );

        if (mapping.isEmpty()) {
            log.warn("Invoice {} not found or access denied for organization {}", 
                    quickbooksInvoiceId, organizationId);
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Invoice not found or access denied"
            );
        }
    }
}
