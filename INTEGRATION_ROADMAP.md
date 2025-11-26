# 🗺️ QuickBooks Integration - Complete Roadmap

## ✅ **COMPLETED FEATURES**

### **1. Customers Sync** ✅
- [x] OAuth 2.0 connection to QuickBooks
- [x] Customer create/update API
- [x] Automatic sync on customer confirmation
- [x] Manual bulk sync endpoint
- [x] Manual single customer sync
- [x] Duplicate detection by name
- [x] Admin dashboard endpoints
- [x] Sync status tracking

### **2. Invoices** ✅ (API Ready)
- [x] Invoice creation API
- [x] Service item auto-selection
- [x] Error handling and logging

### **3. Payments** ✅ (API Ready)
- [x] Payment recording API
- [x] Link to invoices
- [x] Error handling and logging

---

## 🚧 **WHAT'S MISSING - CRITICAL GAPS**

### **❌ Gap 1: No Automatic Invoice Sync**

**Current State:**
```
Metering Service → Generates Invoice → ??? → QuickBooks
                                       ↑
                                  No Connection!
```

**What Needs to Happen:**
```
Metering Service → Invoice Created → Event Published
                                          ↓
                                   Event Listener
                                          ↓
                              Fetch Invoice Details
                                          ↓
                              Map to QuickBooks Format
                                          ↓
                              Call QuickBooks API
                                          ↓
                              Save Mapping ✅
```

**Files to Create:**
1. `InvoiceCreatedEvent.java` - Event when invoice is created
2. `QuickBooksInvoiceListener.java` - Listens for invoice events
3. `InvoiceMapper.java` - Maps your invoice to QuickBooks format
4. `InvoiceServiceClient.java` - Fetches invoice details from your billing service

---

### **❌ Gap 2: No Automatic Payment Sync**

**Current State:**
```
Payment Gateway → Payment Received → ??? → QuickBooks
                                      ↑
                                 No Connection!
```

**What Needs to Happen:**
```
Payment Service → Payment Created → Event Published
                                         ↓
                                  Event Listener
                                         ↓
                             Map Payment to QuickBooks
                                         ↓
                             Find Related Invoice ID
                                         ↓
                             Record in QuickBooks
                                         ↓
                             Update Invoice Status ✅
```

**Files to Create:**
1. `PaymentReceivedEvent.java`
2. `QuickBooksPaymentListener.java`
3. `PaymentMapper.java`

---

### **❌ Gap 3: No Product/Item Sync**

**Current Issue:**
- All invoices use generic "Services" item
- No itemization by product/rate plan
- Poor reporting in QuickBooks

**Should Be:**
```
Rate Plan 1 → QuickBooks Item: "Basic Plan"
Rate Plan 2 → QuickBooks Item: "Pro Plan"
Feature A   → QuickBooks Item: "Additional Storage"
```

---

## 📋 **IMPLEMENTATION PLAN**

### **Phase 1: Invoice Integration** 🔴 **START HERE**

#### **Task 1.1: Identify Invoice Source**
- [ ] Where are invoices created in your system?
  - [ ] Metering service?
  - [ ] Billing service?
  - [ ] Organization service?
- [ ] What format are they stored in?
- [ ] What triggers invoice creation?

#### **Task 1.2: Create Event & Listener**

**File 1: `src/main/java/aforo/quickbooks/event/InvoiceCreatedEvent.java`**
```java
@Data
@Builder
public class InvoiceCreatedEvent {
    private Long invoiceId;
    private Long organizationId;
    private Long customerId;
    private String invoiceNumber;
    private Instant createdAt;
}
```

**File 2: `src/main/java/aforo/quickbooks/listener/QuickBooksInvoiceListener.java`**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class QuickBooksInvoiceListener {
    
    private final QuickBooksApiService apiService;
    private final InvoiceServiceClient invoiceClient;  // To fetch invoice details
    private final InvoiceMapper mapper;
    
    @EventListener
    @Async
    public void handleInvoiceCreated(InvoiceCreatedEvent event) {
        log.info("Received invoice created event: {}", event.getInvoiceId());
        
        try {
            // 1. Check if QuickBooks is connected
            // 2. Fetch full invoice details
            // 3. Map to QuickBooks format
            // 4. Sync to QuickBooks
            
        } catch (Exception e) {
            log.error("Failed to sync invoice: {}", e.getMessage(), e);
        }
    }
}
```

#### **Task 1.3: Create Invoice Mapper**

**File: `src/main/java/aforo/quickbooks/mapper/InvoiceMapper.java`**
```java
@Component
public class InvoiceMapper {
    
    public QuickBooksInvoiceRequest toQuickBooksFormat(YourInvoice invoice) {
        // Map your invoice structure to QuickBooks format
        return QuickBooksInvoiceRequest.builder()
            .CustomerRef(/* customer mapping */)
            .Line(/* map line items */)
            .build();
    }
}
```

#### **Task 1.4: Emit Events from Source**

Update your invoice creation code:
```java
// In your billing/metering service
public Invoice createInvoice(...) {
    Invoice invoice = // create invoice
    
    // Emit event
    eventPublisher.publishEvent(
        InvoiceCreatedEvent.builder()
            .invoiceId(invoice.getId())
            .organizationId(invoice.getOrganizationId())
            .build()
    );
    
    return invoice;
}
```

#### **Task 1.5: Testing**
- [ ] Create test invoice
- [ ] Verify event is published
- [ ] Verify listener receives it
- [ ] Verify syncs to QuickBooks
- [ ] Verify mapping is saved

---

### **Phase 2: Payment Integration** 🟠

#### **Task 2.1: Payment Event & Listener**

Similar structure to invoices:
1. Create `PaymentReceivedEvent`
2. Create `QuickBooksPaymentListener`
3. Create `PaymentMapper`
4. Emit events from payment service
5. Test

#### **Task 2.2: Invoice-Payment Linking**

Key challenge: Link payment to correct invoice
```java
// Must fetch invoice QB ID from mapping
String qbInvoiceId = apiService.getQuickBooksId(
    orgId, 
    EntityType.INVOICE, 
    payment.getInvoiceId()
);

paymentRequest.setLinkedInvoiceRef(qbInvoiceId);
```

---

### **Phase 3: Product/Item Sync** 🟡

#### **Task 3.1: Sync Rate Plans as Items**

```java
@PostMapping("/admin/sync-products")
public ResponseEntity syncProducts(@RequestParam Long organizationId) {
    // Fetch all rate plans
    // For each rate plan:
    //   - Create QuickBooks Item
    //   - Save mapping
    
    return ResponseEntity.ok(/* summary */);
}
```

#### **Task 3.2: Use Specific Items in Invoices**

Update invoice mapper to use rate plan's QB item ID instead of generic "Services"

---

### **Phase 4: Status Polling & Two-Way Sync** 🟢

#### **Task 4.1: Poll Invoice Status**

```java
@Scheduled(cron = "0 0 * * * *") // Hourly
public void syncInvoiceStatus() {
    // Fetch invoices from QuickBooks
    // Compare with local database
    // Update status if changed
}
```

#### **Task 4.2: Handle QB Changes**

- Invoice paid in QB → Update your system
- Invoice voided in QB → Mark as void locally
- Payment deleted in QB → Update records

---

## 🎯 **QUICK START GUIDE**

### **To Complete Invoice Integration TODAY:**

1. **Find where invoices are created:**
   ```bash
   # Search your codebase
   grep -r "Invoice" --include="*Service.java"
   ```

2. **Create the event:**
   ```java
   // Add to your invoice creation code
   eventPublisher.publishEvent(new InvoiceCreatedEvent(...));
   ```

3. **Create the listener:**
   ```java
   @EventListener
   public void handleInvoiceCreated(InvoiceCreatedEvent event) {
       // Sync to QuickBooks
   }
   ```

4. **Test:**
   - Create an invoice
   - Check logs for event
   - Verify sync to QuickBooks

---

## 📊 **CURRENT VS TARGET STATE**

### **Current State:**
```
✅ Customers → Auto-sync
⚠️  Invoices → Manual API only
⚠️  Payments → Manual API only
❌ Products → Not synced
❌ Status   → No two-way sync
```

### **Target State:**
```
✅ Customers → Auto-sync
✅ Invoices  → Auto-sync on creation
✅ Payments  → Auto-sync on receipt
✅ Products  → Synced with rate plans
✅ Status    → Two-way sync (hourly)
```

---

## 🔧 **TECHNICAL REQUIREMENTS**

### **Dependencies Already Present:**
- ✅ Spring Events (`ApplicationEventPublisher`)
- ✅ WebClient for API calls
- ✅ Event logging
- ✅ Mapping repository

### **Need to Add:**
- [ ] Event classes
- [ ] Listener classes
- [ ] Mapper classes
- [ ] Service clients (to fetch data from other services)

---

## 💡 **KEY QUESTIONS TO ANSWER**

Before implementing, clarify:

1. **Invoice Source:**
   - Where are invoices stored?
   - What service creates them?
   - What format are they in?

2. **Payment Source:**
   - Payment gateway used?
   - Where are payments recorded?
   - How are they linked to invoices?

3. **Products:**
   - Do you have rate plans?
   - Do you have product catalog?
   - Should each be a QB item?

4. **Timing:**
   - Sync immediately or batch?
   - Retry failed syncs?
   - How to handle errors?

---

## 📞 **NEXT STEPS**

**Immediate Action:**
1. Answer the key questions above
2. Identify invoice creation location
3. Add event publishing
4. Create event listener
5. Test invoice sync

**Timeline:**
- **Week 1:** Invoice auto-sync
- **Week 2:** Payment auto-sync
- **Week 3:** Product sync
- **Week 4:** Status polling
- **Week 5:** Testing & refinement

---

## 🎯 **SUCCESS CRITERIA**

Integration is complete when:
- [x] Customers auto-sync ✅
- [ ] Invoices auto-sync when created
- [ ] Payments auto-sync when received
- [ ] Products/items synced
- [ ] Status polling active
- [ ] Error handling robust
- [ ] Admin dashboards show all sync status
- [ ] No manual API calls needed for normal operations

---

**The foundation is solid. Now we need to connect the dots between your billing/metering services and the QuickBooks integration!**
