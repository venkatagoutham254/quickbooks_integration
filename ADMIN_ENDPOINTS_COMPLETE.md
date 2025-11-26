# 🎯 **QUICKBOOKS ADMIN ENDPOINTS - COMPLETE IMPLEMENTATION**

## ✅ **WHAT I IMPLEMENTED**

All QuickBooks admin functionality is now **properly centralized** in the `quickbooks_integration` service where it belongs!

---

## 📦 **NEW FILES CREATED**

### **1. Configuration**
```
src/main/java/aforo/quickbooks/config/
└── OrganizationServiceProperties.java ✅ NEW
    - Configures organization service URL
    - Timeout settings
```

### **2. DTOs**
```
src/main/java/aforo/quickbooks/dto/
└── OrganizationCustomerDTO.java ✅ NEW
    - Represents customer data from organization service
```

### **3. Client**
```
src/main/java/aforo/quickbooks/client/
└── OrganizationServiceClient.java ✅ NEW
    - WebClient to call organization service APIs
    - Get active customers
    - Get specific customer
```

### **4. Mapper**
```
src/main/java/aforo/quickbooks/mapper/
└── CustomerMapper.java ✅ NEW
    - Maps OrganizationCustomerDTO → AforoCustomerRequest
```

### **5. Controllers**
```
src/main/java/aforo/quickbooks/controller/
├── QuickBooksSyncStatusController.java ✅ (already created)
│   - GET /api/quickbooks/sync-stats
│   - GET /api/quickbooks/synced-customers
│   - GET /api/quickbooks/customer/{id}/sync-status
│
└── QuickBooksAdminController.java ✅ NEW
    - POST /api/quickbooks/admin/bulk-sync-customers
    - POST /api/quickbooks/admin/sync-customer/{customerId}
    - GET  /api/quickbooks/admin/customer-overview
    - GET  /api/quickbooks/admin/unsynced-customers
```

### **6. Configuration**
```
src/main/resources/
└── application.yml ✅ UPDATED
    - Added organization service configuration
```

---

## 🎯 **ADMIN ENDPOINTS**

### **1. POST /api/quickbooks/admin/bulk-sync-customers**

**Purpose:** Sync all ACTIVE customers from organization service to QuickBooks

**How it works:**
1. Calls organization service to get all ACTIVE customers
2. Checks which ones are already synced (mapping table)
3. Syncs only customers NOT yet in QuickBooks
4. Returns detailed results

**Example:**
```powershell
$token = "YOUR_JWT_TOKEN"
$headers = @{ "Authorization" = "Bearer $token" }

Invoke-RestMethod `
    -Uri "http://localhost:8095/api/quickbooks/admin/bulk-sync-customers" `
    -Method POST `
    -Headers $headers
```

**Response:**
```json
{
  "organizationId": 4,
  "totalActiveCustomers": 3,
  "alreadySynced": 1,
  "attemptedToSync": 2,
  "successCount": 2,
  "failureCount": 0,
  "syncResults": [
    {
      "customerId": 4,
      "aforoId": "CUST-4",
      "customerName": "John Doe",
      "quickBooksId": "123",
      "status": "SUCCESS"
    },
    {
      "customerId": 5,
      "aforoId": "CUST-5",
      "customerName": "Jane Smith",
      "quickBooksId": "124",
      "status": "SUCCESS"
    }
  ],
  "message": "Bulk sync completed: 2 succeeded, 0 failed out of 2 customers"
}
```

---

### **2. POST /api/quickbooks/admin/sync-customer/{customerId}**

**Purpose:** Manually sync a specific customer

**Example:**
```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8095/api/quickbooks/admin/sync-customer/5" `
    -Method POST `
    -Headers $headers
```

**Response:**
```json
{
  "success": true,
  "customerId": 5,
  "aforoId": "CUST-5",
  "customerName": "Jane Smith",
  "quickBooksId": "124",
  "action": "CREATE",
  "message": "Customer Jane Smith successfully created in QuickBooks"
}
```

---

### **3. GET /api/quickbooks/admin/customer-overview**

**Purpose:** See sync statistics - how many customers are synced vs not synced

**Example:**
```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8095/api/quickbooks/admin/customer-overview" `
    -Headers $headers
```

**Response:**
```json
{
  "organizationId": 4,
  "totalActiveCustomers": 3,
  "syncedToQuickBooks": 3,
  "notSyncedToQuickBooks": 0,
  "unsyncedCustomers": [],
  "syncPercentage": 100.0
}
```

---

### **4. GET /api/quickbooks/admin/unsynced-customers**

**Purpose:** Get list of ACTIVE customers NOT synced to QuickBooks

**Example:**
```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8095/api/quickbooks/admin/unsynced-customers" `
    -Headers $headers
```

**Response:**
```json
{
  "organizationId": 4,
  "total": 0,
  "customers": [],
  "message": "0 customers are ACTIVE but not synced to QuickBooks"
}
```

---

## 📊 **ACCURATE SYNC STATUS ENDPOINTS**

### **GET /api/quickbooks/sync-stats**

**Purpose:** Get accurate count of synced entities from mapping table

**Response:**
```json
{
  "organizationId": 4,
  "totalSyncedEntities": 3,
  "syncedCustomers": 3,
  "syncedInvoices": 0,
  "syncedPayments": 0,
  "syncedCustomerIds": ["CUST-4", "CUST-5", "CUST-6"],
  "message": "3 customer(s) actually synced to QuickBooks"
}
```

### **GET /api/quickbooks/synced-customers**

**Purpose:** Get detailed list of synced customers

**Response:**
```json
{
  "customers": [
    {
      "aforoId": "CUST-4",
      "quickbooksId": "123",
      "syncedAt": "2025-11-18T06:30:00Z",
      "lastUpdated": "2025-11-18T06:30:00Z"
    }
  ],
  "total": 1,
  "organizationId": 4
}
```

### **GET /api/quickbooks/customer/{customerId}/sync-status**

**Purpose:** Check if specific customer is synced

**Response:**
```json
{
  "customerId": "CUST-4",
  "organizationId": 4,
  "isSynced": true,
  "quickbooksId": "123",
  "syncedAt": "2025-11-18T06:30:00Z",
  "message": "Customer is synced to QuickBooks"
}
```

---

## 🔄 **COMPLETE WORKFLOW**

### **Scenario: 3 ACTIVE customers, only 1 synced**

```powershell
$token = "YOUR_JWT_TOKEN"
$headers = @{ "Authorization" = "Bearer $token" }

# Step 1: Check overview
$overview = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/admin/customer-overview" -Headers $headers
Write-Host "Total ACTIVE: $($overview.totalActiveCustomers)"
Write-Host "Synced: $($overview.syncedToQuickBooks)"
Write-Host "Not Synced: $($overview.notSyncedToQuickBooks)"

# Step 2: See which customers are NOT synced
$unsynced = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/admin/unsynced-customers" -Headers $headers
Write-Host "Un-synced customers:"
$unsynced.customers | ForEach-Object {
    Write-Host "  - $($_.customerName) ($($_.aforoId))"
}

# Step 3: Bulk sync all un-synced customers
$bulkResult = Invoke-RestMethod `
    -Uri "http://localhost:8095/api/quickbooks/admin/bulk-sync-customers" `
    -Method POST `
    -Headers $headers

Write-Host "Bulk sync: $($bulkResult.successCount) succeeded, $($bulkResult.failureCount) failed"

# Step 4: Verify all are synced now
$stats = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/sync-stats" -Headers $headers
Write-Host "Now synced: $($stats.syncedCustomers) customers"
```

---

## 🎯 **ARCHITECTURE BENEFITS**

### **✅ Clean Separation**

**Organization Service:**
- Customer CRUD operations
- Publish sync events
- Call QuickBooks service

**QuickBooks Integration Service:**
- QuickBooks OAuth
- QuickBooks API calls
- Sync logic
- Admin operations ✅
- Sync status tracking ✅

### **✅ Single Source of Truth**

All QuickBooks operations in one place:
- Easy to maintain
- Easy to test
- Easy to extend

### **✅ Accurate Statistics**

No more misleading numbers:
- Checks actual mapping table
- Shows real sync status
- Clear distinction between ACTIVE and synced

---

## 📝 **TESTING GUIDE**

### **Prerequisites:**
1. QuickBooks Integration service running on port 8095
2. Organization service running on port 8081
3. QuickBooks connected for your organization
4. JWT token from organization service

### **Test Sequence:**

```powershell
# 1. Get JWT token
$response = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body '{"email":"user@example.com","password":"password"}'
$token = $response.token
$headers = @{ "Authorization" = "Bearer $token" }

# 2. Check QuickBooks connection
$status = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/status" -Headers $headers
if (-not $status.connected) {
    Write-Host "❌ QuickBooks not connected. Connect first!"
    exit
}

# 3. Get customer overview
$overview = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/admin/customer-overview" -Headers $headers
Write-Host "📊 Customer Overview:"
Write-Host "   Total ACTIVE: $($overview.totalActiveCustomers)"
Write-Host "   Synced: $($overview.syncedToQuickBooks)"
Write-Host "   Not Synced: $($overview.notSyncedToQuickBooks)"
Write-Host "   Sync %: $($overview.syncPercentage)%"

# 4. Bulk sync if needed
if ($overview.notSyncedToQuickBooks -gt 0) {
    Write-Host ""
    Write-Host "🔄 Starting bulk sync..."
    $bulk = Invoke-RestMethod `
        -Uri "http://localhost:8095/api/quickbooks/admin/bulk-sync-customers" `
        -Method POST `
        -Headers $headers
    Write-Host "✅ Synced: $($bulk.successCount)"
    Write-Host "❌ Failed: $($bulk.failureCount)"
}

# 5. Verify sync stats
$stats = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/sync-stats" -Headers $headers
Write-Host ""
Write-Host "📊 Final Stats:"
Write-Host "   Synced Customers: $($stats.syncedCustomers)"
Write-Host "   Customer IDs: $($stats.syncedCustomerIds -join ', ')"
```

---

## 🛠️ **CONFIGURATION**

### **application.yml:**
```yaml
aforo:
  organization-service:
    base-url: http://host.docker.internal:8081  # For Docker
    timeout-seconds: 30
```

For non-Docker setup:
```yaml
aforo:
  organization-service:
    base-url: http://localhost:8081
    timeout-seconds: 30
```

---

## ✅ **SUMMARY**

### **What Was Implemented:**
1. ✅ Organization Service client
2. ✅ Customer mapping utilities
3. ✅ Bulk sync admin endpoint
4. ✅ Manual sync endpoint
5. ✅ Customer overview endpoint
6. ✅ Un-synced customers endpoint
7. ✅ Accurate sync statistics endpoints

### **Key Features:**
- ✅ **Intelligent bulk sync** - Only syncs customers not already in QuickBooks
- ✅ **Accurate statistics** - Checks actual mapping table
- ✅ **Detailed results** - Know exactly what succeeded/failed
- ✅ **Manual intervention** - Sync specific customers
- ✅ **Clear overview** - See what's synced vs not synced

### **Architecture:**
- ✅ **Clean separation** - QuickBooks logic in QuickBooks service
- ✅ **Single responsibility** - Each service does what it should
- ✅ **Accurate data** - No misleading numbers

---

**All QuickBooks admin operations are now properly centralized in the quickbooks_integration service!** 🎉
