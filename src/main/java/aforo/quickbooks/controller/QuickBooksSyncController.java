package aforo.quickbooks.controller;

import aforo.quickbooks.dto.*;
import aforo.quickbooks.service.QuickBooksApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for QuickBooks data synchronization.
 */
@RestController
@RequestMapping("/api/quickbooks/sync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QuickBooks Sync", description = "Endpoints for syncing data with QuickBooks")
public class QuickBooksSyncController {

    private final QuickBooksApiService apiService;

    @PostMapping("/customer")
    @Operation(summary = "Sync customer to QuickBooks", 
              description = "Create or update customer in QuickBooks using Aforo customer format")
    public ResponseEntity<Map<String, String>> syncCustomer(
            @RequestParam Long organizationId,
            @RequestParam String aforoCustomerId,
            @RequestBody AforoCustomerRequest customerRequest) {
        
        log.info("Syncing customer {} for org: {}", aforoCustomerId, organizationId);
        
        try {
            String qbCustomerId = apiService.syncCustomer(organizationId, aforoCustomerId, customerRequest);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("aforoCustomerId", aforoCustomerId);
            response.put("quickbooksCustomerId", qbCustomerId);
            response.put("message", "Customer synced successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to sync customer: {}", e.getMessage(), e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/invoice")
    @Operation(summary = "Create invoice in QuickBooks", 
              description = "Create invoice in QuickBooks")
    public ResponseEntity<Map<String, String>> createInvoice(
            @RequestParam Long organizationId,
            @RequestParam String aforoInvoiceId,
            @RequestBody QuickBooksInvoiceRequest invoiceRequest) {
        
        log.info("Creating invoice {} for org: {}", aforoInvoiceId, organizationId);
        
        try {
            String qbInvoiceId = apiService.createInvoice(organizationId, aforoInvoiceId, invoiceRequest);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("aforoInvoiceId", aforoInvoiceId);
            response.put("quickbooksInvoiceId", qbInvoiceId);
            response.put("message", "Invoice created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to create invoice: {}", e.getMessage(), e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/payment")
    @Operation(summary = "Record payment in QuickBooks", 
              description = "Record payment in QuickBooks")
    public ResponseEntity<Map<String, String>> recordPayment(
            @RequestParam Long organizationId,
            @RequestParam String aforoPaymentId,
            @RequestBody QuickBooksPaymentRequest paymentRequest) {
        
        log.info("Recording payment {} for org: {}", aforoPaymentId, organizationId);
        
        try {
            String qbPaymentId = apiService.recordPayment(organizationId, aforoPaymentId, paymentRequest);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("aforoPaymentId", aforoPaymentId);
            response.put("quickbooksPaymentId", qbPaymentId);
            response.put("message", "Payment recorded successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to record payment: {}", e.getMessage(), e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}
