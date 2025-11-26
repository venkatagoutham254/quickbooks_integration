# 📊 METERING SERVICE - COMPLETE ANALYSIS

## 🎯 **SERVICE OVERVIEW**

**Port:** 8092  
**Purpose:** Calculate usage-based billing from ingestion events  
**Database:** Reads from `data_ingestion_db` (port 5436)  
**Table:** `ingestion_event` (read-only access)

---

## 🏗️ **ARCHITECTURE**

```
┌────────────────────────────────────────────────────────────────┐
│                    METERING SERVICE FLOW                       │
└────────────────────────────────────────────────────────────────┘

1. INGESTION SERVICE
   └─> Writes usage events to ingestion_event table
       ├─ timestamp
       ├─ organization_id
       ├─ subscription_id
       ├─ product_id
       ├─ rate_plan_id
       ├─ billable_metric_id
       └─ status: SUCCESS

2. METERING SERVICE
   └─> Reads ingestion_event table
       └─> Counts events (each event = 1 billable unit)
           └─> Fetches Rate Plan config
               └─> Applies pricing model
                   └─> Returns: LineItems + Total Cost

3. OUTPUT (MeterResponse)
   {
     "modelType": "FLATFEE",
     "breakdown": [
       {
         "label": "Flat Fee",
         "calculation": "Base",
         "amount": 100
       },
       {
         "label": "Overage Charges",
         "calculation": "1 * 110.00",
         "amount": 110
       }
     ],
     "total": 210
   }
```

---

## 📁 **KEY FILES & COMPONENTS**

### **1. Controller: `MeterController.java`**

**Endpoints:**

1. **POST `/api/meter`** - Calculate usage cost
   ```json
   Request:
   {
     "from": "2025-11-13T15:01:03Z",
     "to": "2025-11-21T01:01:13Z",
     "ratePlanId": 1,
     "subscriptionId": 1
   }
   
   Response:
   {
     "modelType": "FLATFEE",
     "breakdown": [
       { "label": "Flat Fee", "calculation": "Base", "amount": 100 },
       { "label": "Overage Charges", "calculation": "1 * 110.00", "amount": 110 }
     ],
     "total": 210
   }
   ```

2. **POST `/api/meter/trigger`** - Auto-trigger after ingestion
   - Called by ingestion service after events processed
   - Async processing

3. **POST `/api/meter/batch`** - Batch process multiple rate plans
   - For end-of-period billing

---

### **2. Service: `MeterServiceImpl.java`**

**Core Logic:**

```java
public MeterResponse estimate(MeterRequest request) {
    // 1. Count events from ingestion_event table
    BigDecimal eventCount = usageRepository.countEvents(...);
    
    // 2. Fetch rate plan configuration
    RatePlanDTO ratePlan = ratePlanClient.fetchRatePlan(ratePlanId);
    
    // 3. Apply pricing model
    if (ratePlan.getFlatFee() != null) {
        // Flat fee + overage
    }
    if (ratePlan.getUsageBasedPricings() != null) {
        // Per-unit pricing
    }
    if (ratePlan.getTieredPricings() != null) {
        // Tiered pricing
    }
    // ... volume, stairstep, etc.
    
    // 4. Apply extras (setup, discounts, freemium, minimum commitment)
    
    // 5. Return breakdown + total
    return MeterResponse with line items;
}
```

**Supported Pricing Models:**
- ✅ Flat Fee with included units + overage
- ✅ Usage-Based (per unit)
- ✅ Tiered Pricing
- ✅ Volume Pricing
- ✅ Stair-Step Pricing
- ✅ Setup Fees
- ✅ Discounts (percentage & flat)
- ✅ Freemium credits
- ✅ Minimum Commitment

---

### **3. Repository: `UsageRepository.java`**

**Purpose:** Query `ingestion_event` table

```java
public BigDecimal countEvents(
    Long orgId,
    Instant from,
    Instant to,
    Long subscriptionId,
    Long productId,
    Long ratePlanId,
    Long metricId
) {
    // SELECT COUNT(*)::numeric 
    // FROM ingestion_event 
    // WHERE status = 'SUCCESS'
    //   AND organization_id = :org
    //   AND timestamp >= :from 
    //   AND timestamp < :to
    //   AND ...filters...
    
    return count;
}
```

**Important:** Each event = 1 billable unit (no quantity field)

---

### **4. Client: `RatePlanClient.java`**

**Purpose:** Fetch rate plan configuration from Rate Plan Service

```java
public RatePlanDTO fetchRatePlan(Long ratePlanId) {
    // GET http://3.208.93.68:8080/api/rateplans/{id}
    // Headers:
    //   - X-Organization-Id
    //   - Authorization: Bearer {JWT}
    
    return RatePlanDTO;
}
```

**Fallback Logic:**
- If single GET fails with 5xx → Fetch all rate plans and filter locally

---

### **5. DTO: `RatePlanDTO.java`**

**Structure:**
```java
class RatePlanDTO {
    Long ratePlanId;
    String billingFrequency;
    
    // Pricing Models
    FlatFeeDTO flatFee;
    List<TieredPricingDTO> tieredPricings;
    List<VolumePricingDTO> volumePricings;
    List<UsageBasedPricingDTO> usageBasedPricings;
    List<StairStepPricingDTO> stairStepPricings;
    
    // Extras
    List<SetupFeeDTO> setupFees;
    List<DiscountDTO> discounts;
    List<FreemiumDTO> freemiums;
    List<MinimumCommitmentDTO> minimumCommitments;
}
```

---

### **6. DTO: `MeterResponse.java`**

**Output Format:**
```java
class MeterResponse {
    String modelType;              // "FLATFEE", "USAGE_BASED", etc.
    List<LineItem> breakdown;      // Detailed line items
    BigDecimal total;              // Final charge
    
    class LineItem {
        String label;              // "Flat Fee", "Overage Charges"
        String calculation;        // "Base", "1 * 110.00"
        BigDecimal amount;         // 100, 110, -50 (negative = credit)
    }
}
```

---

### **7. Auto-Metering: `AutoMeteringService.java`**

**Three Modes:**

1. **Async Trigger (after ingestion)**
   ```java
   @Async
   public void processMeteringForSubscription(
       Long subscriptionId, 
       Long ratePlanId, 
       Instant from, 
       Instant to
   ) {
       // Calculate and log
       // TODO: Store result or create invoice
   }
   ```

2. **Scheduled (hourly)**
   ```java
   @Scheduled(cron = "0 0 * * * *")
   public void processPeriodicalMetering() {
       // Process all rate plans with new events
       // TODO: Store results
   }
   ```

3. **Batch (manual/API)**
   ```java
   public void processBatchMetering(
       List<Long> ratePlanIds, 
       Instant from, 
       Instant to
   ) {
       // Process multiple rate plans
   }
   ```

**⚠️ IMPORTANT:** All have `// TODO: Store result or send to billing system`

---

## 🔗 **EXTERNAL DEPENDENCIES**

### **Rate Plan Service** (Port 8080)
- Base URL: `http://3.208.93.68:8080`
- Endpoint: `GET /api/rateplans/{id}`
- Auth: JWT + X-Organization-Id header

### **Billable Metric Service** (Port 8081)
- Base URL: `http://34.238.49.158:8081`
- Not actively used in current implementation

### **Subscription Service** (Port 8084)
- Base URL: `http://52.90.125.218:8084`
- Not actively used in current implementation

### **Customer Service** (Port 8081)
- Base URL: `http://44.201.19.187:8081`
- Not actively used in current implementation

---

## 🔑 **KEY INSIGHTS**

### **1. NO INVOICE CREATION** ⚠️
```java
// From AutoMeteringService.java:
log.info("Metering completed. Total cost: {}", response.getTotal());

// TODO: Store the metering result in database or send to billing system
```

**Current State:**
- ✅ Calculates usage
- ✅ Applies pricing
- ✅ Returns breakdown
- ❌ Does NOT create invoices
- ❌ Does NOT store results
- ❌ Does NOT trigger billing

---

### **2. EVENT COUNTING**
```java
// Each event in ingestion_event = 1 billable unit
BigDecimal eventCount = usageRepository.countEvents(...);
int usage = eventCount.intValue();
```

**No quantity field** - Simple count of SUCCESS events

---

### **3. RESPONSE FORMAT (FROM SCREENSHOT)**

Your screenshot shows:
```json
{
  "modelType": "FLATFEE",
  "breakdown": [
    {
      "label": "Flat Fee",
      "calculation": "Base",
      "amount": 100
    },
    {
      "label": "Overage Charges",
      "calculation": "1 * 110.00",
      "amount": 110
    }
  ],
  "total": 210
}
```

**This matches the `MeterResponse` structure perfectly!** ✅

---

## 🎯 **QUICKBOOKS INTEGRATION REQUIREMENTS**

### **What Metering Service Provides:**
- ✅ Usage calculation
- ✅ Cost breakdown
- ✅ Line items with labels & amounts
- ✅ Total cost
- ✅ Pricing model type

### **What's Missing for QB Integration:**
1. ❌ Invoice entity/table
2. ❌ Invoice creation logic
3. ❌ Invoice storage
4. ❌ Event publishing when invoice created
5. ❌ Customer linkage to invoice

---

## 🚀 **INTEGRATION STRATEGY**

### **Option 1: Add Invoice Creation to Metering Service** 🟢 **RECOMMENDED**

**Why?** Metering already calculates everything needed for an invoice.

**What to Add:**

1. **Invoice Entity**
   ```java
   @Entity
   @Table(name = "invoice")
   class Invoice {
       Long id;
       Long organizationId;
       Long customerId;
       Long subscriptionId;
       Long ratePlanId;
       String invoiceNumber;
       BigDecimal totalAmount;
       Instant billingPeriodStart;
       Instant billingPeriodEnd;
       String status; // DRAFT, ISSUED, PAID
       Instant createdAt;
       List<InvoiceLineItem> lineItems;
   }
   
   @Entity
   @Table(name = "invoice_line_item")
   class InvoiceLineItem {
       Long id;
       Long invoiceId;
       String description;
       String calculation;
       BigDecimal amount;
   }
   ```

2. **Invoice Service**
   ```java
   @Service
   class InvoiceService {
       
       @Transactional
       public Invoice createInvoice(MeterResponse meterResponse, ...) {
           // 1. Create invoice entity
           Invoice invoice = new Invoice();
           invoice.setTotalAmount(meterResponse.getTotal());
           
           // 2. Create line items from breakdown
           for (LineItem item : meterResponse.getBreakdown()) {
               InvoiceLineItem line = new InvoiceLineItem();
               line.setDescription(item.getLabel());
               line.setCalculation(item.getCalculation());
               line.setAmount(item.getAmount());
               invoice.addLineItem(line);
           }
           
           // 3. Save
           invoiceRepository.save(invoice);
           
           // 4. Publish event
           eventPublisher.publishEvent(
               new InvoiceCreatedEvent(invoice.getId(), ...)
           );
           
           return invoice;
       }
   }
   ```

3. **Update AutoMeteringService**
   ```java
   // Replace TODO with:
   MeterResponse response = meterService.estimate(request);
   
   // Create invoice
   Invoice invoice = invoiceService.createInvoice(
       response,
       subscriptionId,
       customerId,
       from,
       to
   );
   
   log.info("Invoice {} created. Total: {}", 
       invoice.getInvoiceNumber(), 
       invoice.getTotalAmount()
   );
   ```

4. **QuickBooks Integration Listens**
   ```java
   @Component
   class QuickBooksInvoiceListener {
       
       @EventListener
       @Async
       public void handleInvoiceCreated(InvoiceCreatedEvent event) {
           // Fetch invoice
           Invoice invoice = invoiceRepository.findById(event.getInvoiceId());
           
           // Map to QuickBooks format
           QuickBooksInvoiceRequest qbInvoice = 
               invoiceMapper.toQuickBooksFormat(invoice);
           
           // Sync to QuickBooks
           apiService.createInvoice(...);
       }
   }
   ```

---

### **Option 2: Separate Billing Service** 🟡 **MORE COMPLEX**

Create a dedicated billing service that:
- Listens to metering completion
- Creates invoices
- Manages invoice lifecycle
- Publishes events for QuickBooks

**Pros:**
- Clean separation
- Dedicated invoice management

**Cons:**
- Another service to maintain
- More network calls
- More complexity

---

## 📋 **IMPLEMENTATION CHECKLIST**

### **Phase 1: Invoice Creation in Metering Service**

- [ ] Create `Invoice` entity & table
- [ ] Create `InvoiceLineItem` entity & table
- [ ] Create `InvoiceRepository`
- [ ] Create `InvoiceService` with `createInvoice()` method
- [ ] Create `InvoiceCreatedEvent` class
- [ ] Update `AutoMeteringService` to create invoices
- [ ] Add invoice REST endpoints (GET /api/invoices, etc.)
- [ ] Test invoice creation

### **Phase 2: QuickBooks Integration**

- [ ] Create `InvoiceMapper` in QuickBooks service
- [ ] Create `QuickBooksInvoiceListener`
- [ ] Map `MeterResponse.LineItem` to QuickBooks line format
- [ ] Handle customer ID mapping
- [ ] Test end-to-end flow
- [ ] Monitor sync logs

---

## 💡 **KEY DATA MAPPINGS**

### **MeterResponse → QuickBooks Invoice**

```
MeterResponse.breakdown → QuickBooks Invoice Lines
├─ LineItem.label → Line.Description
├─ LineItem.calculation → Line.Description (append)
└─ LineItem.amount → Line.Amount

MeterResponse.total → Invoice.TotalAmt

Additional needed:
├─ customerId → CustomerRef
├─ invoiceNumber → DocNumber
├─ billingPeriod → TxnDate, DueDate
└─ serviceItemId → Line.SalesItemLineDetail.ItemRef
```

---

## 🎯 **RECOMMENDED NEXT STEPS**

1. **Immediate:**
   - Add Invoice entity to metering service
   - Create invoice when metering completes
   - Publish `InvoiceCreatedEvent`

2. **QuickBooks Integration:**
   - Create listener in QB service
   - Map invoice to QB format
   - Test sync

3. **Testing:**
   - Trigger metering API
   - Verify invoice created
   - Verify QB sync triggered
   - Check QB dashboard

---

## 🔍 **CURRENT STATE SUMMARY**

**✅ What Works:**
- Metering calculation
- Pricing models
- Cost breakdown
- Line items
- Auto-triggering

**❌ What's Missing:**
- Invoice creation
- Invoice storage
- Event publishing
- QB integration trigger

**🎯 Gap:**
```
Metering → [INVOICE CREATION MISSING] → QuickBooks
```

**Solution:**
```
Metering → CREATE INVOICE → Publish Event → QB Listener → Sync ✅
```

---

**The metering service is 90% ready! Just need to add invoice creation and event publishing to complete the QuickBooks integration chain.** 🚀
