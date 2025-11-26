# 🏗️ QuickBooks Integration - Architecture & Data Flow

## 📊 **COMPLETE SYSTEM ARCHITECTURE**

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        AFORO SYSTEM ARCHITECTURE                           │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────┐         ┌──────────────────────┐
│ ORGANIZATION SERVICE │         │  METERING SERVICE    │
│   (Port 8081)        │         │    (Port 8083)       │
│                      │         │                      │
│ • Customers          │         │ • Usage Events       │
│ • Customer CRUD      │         │ • Rate Plans         │
│ • Status: DRAFT/ACTIVE│        │ • Calculations       │
│ • Emits Events       │         │ • Invoices?          │
└──────────┬───────────┘         └──────────┬───────────┘
           │                                 │
           │ CustomerActivatedEvent          │ InvoiceCreatedEvent?
           │                                 │
           ▼                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│              QUICKBOOKS INTEGRATION SERVICE (Port 8095)                │
│                                                                        │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │ Event Listeners  │  │  API Service     │  │  Mappers         │   │
│  │                  │  │                  │  │                  │   │
│  │ • Customer ✅    │  │ • syncCustomer   │  │ • CustomerMapper │   │
│  │ • Invoice  ❌    │  │ • createInvoice  │  │ • InvoiceMapper  │   │
│  │ • Payment  ❌    │  │ • recordPayment  │  │ • PaymentMapper  │   │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘   │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │              MAPPING DATABASE                                 │    │
│  │  aforo_customer_123 ←→ qb_customer_456                       │    │
│  │  aforo_invoice_789  ←→ qb_invoice_101                        │    │
│  └──────────────────────────────────────────────────────────────┘    │
└────────────────────────────┬───────────────────────────────────────────┘
                             │
                             ▼
                   ┌───────────────────┐
                   │  QUICKBOOKS API   │
                   │                   │
                   │ • Customers       │
                   │ • Invoices        │
                   │ • Payments        │
                   │ • Items           │
                   └───────────────────┘
```

---

## 🔄 **CURRENT DATA FLOW**

### **1. Customer Sync (✅ WORKING)**

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CUSTOMER SYNC FLOW                           │
└─────────────────────────────────────────────────────────────────────┘

Step 1: User Action
   User creates customer → Status: DRAFT
   User clicks "Confirm" → Status: ACTIVE

Step 2: Event Published
   Organization Service emits:
   CustomerActivatedEvent {
       customerId: 123,
       organizationId: 2
   }

Step 3: Event Received
   QuickBooksCustomerListener receives event

Step 4: Check Connection
   Is QuickBooks connected for org 2?
   ├─ NO  → Skip (log warning)
   └─ YES → Continue

Step 5: Fetch Customer Data
   Call Organization Service:
   GET /v1/api/customers/123
   
   Response: {
       "customerId": 123,
       "customerName": "Acme Corp",
       "email": "acme@example.com",
       ...
   }

Step 6: Map to QuickBooks Format
   CustomerMapper converts:
   Aforo Customer → QuickBooks Customer JSON

Step 7: Check if Already Synced
   Query mapping table:
   SELECT qb_id FROM mapping 
   WHERE aforo_id = 'customer-123'
   
   ├─ Found → UPDATE existing customer
   └─ Not Found → CREATE new customer

Step 8: Sync to QuickBooks
   POST https://quickbooks.api.intuit.com/.../customer
   {
       "DisplayName": "Acme Corp",
       "PrimaryEmailAddr": {"Address": "acme@example.com"},
       ...
   }
   
   Response: { "Customer": { "Id": "456", ... } }

Step 9: Save Mapping
   INSERT INTO mapping (
       aforo_id: 'customer-123',
       qb_id: '456',
       entity_type: 'CUSTOMER'
   )

Step 10: Log Success ✅
   Sync complete!
```

---

### **2. Invoice Sync (❌ NOT WORKING - NEEDS IMPLEMENTATION)**

```
┌─────────────────────────────────────────────────────────────────────┐
│               INVOICE SYNC FLOW (WHAT SHOULD HAPPEN)                │
└─────────────────────────────────────────────────────────────────────┘

Step 1: Invoice Creation
   Metering Service calculates usage
   Creates invoice for billing period
   
   Invoice {
       id: 789,
       customerId: 123,
       amount: $150.00,
       lineItems: [...]
   }

Step 2: ❌ MISSING: Event Publishing
   SHOULD emit:
   InvoiceCreatedEvent {
       invoiceId: 789,
       customerId: 123,
       organizationId: 2
   }
   
   ⚠️ Currently: No event published!

Step 3: ❌ MISSING: Event Listener
   SHOULD have:
   @EventListener
   handleInvoiceCreated(event)
   
   ⚠️ Currently: No listener exists!

Step 4-10: Same as Customer Sync
   - Fetch invoice details
   - Map to QuickBooks format
   - Check if synced
   - Sync to QuickBooks
   - Save mapping

CURRENT STATE:
  Manual API call required:
  POST /api/quickbooks/sync/invoice?invoiceId=789

TARGET STATE:
  Automatic sync when invoice created
```

---

### **3. Payment Sync (❌ NOT WORKING - NEEDS IMPLEMENTATION)**

```
┌─────────────────────────────────────────────────────────────────────┐
│              PAYMENT SYNC FLOW (WHAT SHOULD HAPPEN)                 │
└─────────────────────────────────────────────────────────────────────┘

Step 1: Payment Received
   Payment Gateway processes payment
   Payment recorded in system
   
   Payment {
       id: 555,
       invoiceId: 789,
       amount: $150.00,
       method: "Credit Card"
   }

Step 2: ❌ MISSING: Event Publishing
   SHOULD emit:
   PaymentReceivedEvent {
       paymentId: 555,
       invoiceId: 789,
       customerId: 123,
       organizationId: 2
   }

Step 3: ❌ MISSING: Event Listener
   SHOULD have:
   @EventListener
   handlePaymentReceived(event)

Step 4: Link to Invoice
   Find QuickBooks Invoice ID:
   qbInvoiceId = mapping.get('invoice-789')
   
   Build payment with link:
   Payment {
       CustomerRef: qbCustomerId,
       TotalAmt: 150.00,
       Line: [{
           Amount: 150.00,
           LinkedTxn: [{
               TxnId: qbInvoiceId,
               TxnType: "Invoice"
           }]
       }]
   }

Step 5: Record in QuickBooks
   POST .../payment
   
   This marks the invoice as PAID in QuickBooks ✅

CURRENT STATE:
  Manual API call required
  
TARGET STATE:
  Automatic sync when payment received
```

---

## 🎯 **DATA FLOW COMPARISON**

### **Customer Sync: COMPLETE ✅**

```
Org Service → Event → Listener → Mapper → QB API → Mapping ✅
```

### **Invoice Sync: INCOMPLETE ⚠️**

```
Billing Service → ❌ No Event → ❌ No Listener → Manual API Call
```

### **Payment Sync: INCOMPLETE ⚠️**

```
Payment Service → ❌ No Event → ❌ No Listener → Manual API Call
```

---

## 🔌 **INTEGRATION POINTS**

### **What Exists:**

1. **QuickBooks OAuth** ✅
   - Connect/disconnect
   - Token management
   - Auto-refresh

2. **API Layer** ✅
   - Customer sync
   - Invoice create
   - Payment record

3. **Mapping Storage** ✅
   - Track Aforo ↔ QuickBooks IDs
   - Query by entity type
   - Version tracking

4. **Error Handling** ✅
   - Retry logic
   - Error logging
   - Sync status tracking

### **What's Missing:**

1. **Event Bridge for Invoices** ❌
   - No event published when invoice created
   - No listener to catch the event
   - No automatic trigger

2. **Event Bridge for Payments** ❌
   - No event published when payment received
   - No listener to catch the event
   - No automatic trigger

3. **Service Clients** ❌
   - No client to fetch invoice details
   - No client to fetch payment details

4. **Mappers** ❌
   - Invoice mapper incomplete
   - Payment mapper incomplete

---

## 🛠️ **WHAT NEEDS TO BE BUILT**

### **For Invoice Sync:**

```java
// 1. Event Definition
public class InvoiceCreatedEvent {
    private Long invoiceId;
    private Long organizationId;
    private Long customerId;
}

// 2. Event Publisher (in your billing service)
public Invoice createInvoice(...) {
    Invoice invoice = invoiceRepository.save(...);
    
    // Publish event
    eventPublisher.publishEvent(
        new InvoiceCreatedEvent(
            invoice.getId(),
            invoice.getOrganizationId(),
            invoice.getCustomerId()
        )
    );
    
    return invoice;
}

// 3. Event Listener (in QuickBooks integration)
@Component
public class QuickBooksInvoiceListener {
    
    @EventListener
    @Async
    public void handleInvoiceCreated(InvoiceCreatedEvent event) {
        // 1. Check QB connection
        // 2. Fetch invoice details
        // 3. Map to QB format
        // 4. Sync to QuickBooks
        // 5. Save mapping
    }
}

// 4. Service Client
@Component
public class BillingServiceClient {
    
    public Invoice getInvoice(Long invoiceId) {
        // HTTP call to billing service
        // GET /api/invoices/{invoiceId}
    }
}

// 5. Mapper
@Component
public class InvoiceMapper {
    
    public QuickBooksInvoiceRequest toQuickBooksFormat(Invoice invoice) {
        return QuickBooksInvoiceRequest.builder()
            .CustomerRef(...)
            .Line(...)
            .build();
    }
}
```

---

## 📊 **EVENT-DRIVEN ARCHITECTURE**

```
┌──────────────────────────────────────────────────────────────┐
│              EVENT-DRIVEN SYNC ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────┘

                    APPLICATION EVENT BUS
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│  Customer   │   │  Invoice    │   │  Payment    │
│  Event      │   │  Event      │   │  Event      │
│  ✅ Active  │   │  ❌ Missing │   │  ❌ Missing │
└─────┬───────┘   └─────┬───────┘   └─────┬───────┘
      │                 │                 │
      ▼                 ▼                 ▼
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│  Customer   │   │  Invoice    │   │  Payment    │
│  Listener   │   │  Listener   │   │  Listener   │
│  ✅ Exists  │   │  ❌ Missing │   │  ❌ Missing │
└─────┬───────┘   └─────┬───────┘   └─────┬───────┘
      │                 │                 │
      └─────────────────┴─────────────────┘
                        │
                        ▼
              ┌───────────────────┐
              │ QuickBooks API    │
              │ Service           │
              └───────────────────┘
                        │
                        ▼
              ┌───────────────────┐
              │ QuickBooks Cloud  │
              └───────────────────┘
```

---

## 🎯 **TO COMPLETE THE INTEGRATION**

### **Step 1: Find Your Services**

Answer these questions:
- Where are invoices created? → `_______________`
- Where are payments recorded? → `_______________`
- What format are invoices stored? → `_______________`
- What triggers invoice creation? → `_______________`

### **Step 2: Add Event Publishing**

In your billing/invoice service:
```java
@Autowired
private ApplicationEventPublisher eventPublisher;

// When invoice is created:
eventPublisher.publishEvent(new InvoiceCreatedEvent(...));

// When payment is received:
eventPublisher.publishEvent(new PaymentReceivedEvent(...));
```

### **Step 3: Create Listeners**

In QuickBooks integration service:
```java
@Component
public class QuickBooksInvoiceListener {
    @EventListener
    public void handleInvoiceCreated(InvoiceCreatedEvent event) {
        // Sync logic
    }
}
```

### **Step 4: Test**

1. Create invoice → Check logs for event
2. Verify listener catches event
3. Verify syncs to QuickBooks
4. Verify mapping saved

---

## 🔍 **DEBUGGING FLOW**

If sync doesn't work, check:

1. **Is event published?**
   - Add log in publishing code
   - Verify event reaches bus

2. **Is listener receiving?**
   - Add log at listener entry
   - Check @Component annotation

3. **Is QB connected?**
   - Check connection status
   - Verify tokens valid

4. **Is mapping saved?**
   - Query mapping table
   - Verify both IDs present

5. **Check sync logs:**
   - Query quickbooks_sync_log table
   - Check error messages

---

**The architecture is solid. We just need to connect your billing/metering services to the QuickBooks integration through events!** 🚀
