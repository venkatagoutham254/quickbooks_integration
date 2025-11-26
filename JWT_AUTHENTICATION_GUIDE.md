# 🔐 **QUICKBOOKS INTEGRATION - JWT AUTHENTICATION GUIDE**

## 🎯 **WHAT CHANGED**

### **BEFORE (Old Way)** ❌
```http
GET /api/quickbooks/connect?organizationId=3
GET /api/quickbooks/status?organizationId=3
POST /api/quickbooks/disconnect?organizationId=3
```
**Problem:** Had to pass organizationId manually in every request

---

### **NOW (New Way)** ✅
```http
GET /api/quickbooks/connect
Authorization: Bearer {JWT_TOKEN}

GET /api/quickbooks/status
Authorization: Bearer {JWT_TOKEN}

POST /api/quickbooks/disconnect
Authorization: Bearer {JWT_TOKEN}
```
**Solution:** Organization ID automatically extracted from JWT token!

---

## 🔧 **WHAT I IMPLEMENTED**

### **1. TenantContext.java** ✅
**Purpose:** Thread-local storage for organization ID

**What it does:**
- Stores organization ID per request thread
- Automatically cleared after request completes
- Provides safe access to current organization ID

**Usage:**
```java
// Get organization ID
Long orgId = TenantContext.require();

// Check if set
boolean isSet = TenantContext.isSet();

// Clear (automatic)
TenantContext.clear();
```

---

### **2. JwtTenantFilter.java** ✅
**Purpose:** Extract organization ID from JWT token automatically

**What it does:**
1. Intercepts every request
2. Reads `Authorization: Bearer {token}` header
3. Parses JWT token
4. Extracts `organizationId` claim
5. Stores in `TenantContext`
6. Clears after request completes

**Flow:**
```
Request → JwtTenantFilter → Parse JWT → Extract organizationId → Store in TenantContext → Controller
```

---

### **3. Updated Controllers** ✅

**QuickBooksAuthController.java:**
- ✅ Removed `@RequestParam Long organizationId` from all endpoints
- ✅ Added `TenantContext.require()` to get organization ID
- ✅ Organization ID now extracted from JWT automatically

**Endpoints Updated:**
- `GET /api/quickbooks/connect` - No organizationId parameter needed
- `GET /api/quickbooks/status` - No organizationId parameter needed  
- `POST /api/quickbooks/disconnect` - No organizationId parameter needed

---

### **4. Updated SecurityConfig.java** ✅

**Added:**
- JWT Tenant Filter to security chain
- Filter runs before authentication
- Extracts organization ID before request processing

---

### **5. Added Dependency** ✅

**pom.xml:**
```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.37.3</version>
</dependency>
```
For JWT parsing in the filter

---

## 🧪 **HOW TO TEST**

### **Step 1: Get JWT Token from Organization Service**

```powershell
# Login to organization service
$loginBody = @{
    email = "admin@yourorg.com"
    password = "your_password"
} | ConvertTo-Json

$response = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body $loginBody

$token = $response.token
Write-Host "JWT Token: $token"
```

**Important:** Your JWT token MUST contain `organizationId` claim:
```json
{
  "sub": "user@example.com",
  "organizationId": 1,   ← REQUIRED
  "exp": 1234567890,
  ...
}
```

---

### **Step 2: Test QuickBooks Connect (NO organizationId parameter)**

```powershell
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# OLD WAY (Don't do this anymore):
# GET /api/quickbooks/connect?organizationId=3  ❌

# NEW WAY (Do this):
$response = Invoke-RestMethod `
    -Uri "http://localhost:8095/api/quickbooks/connect" `
    -Headers $headers

Write-Host "Auth URL: $($response.authUrl)"
Write-Host "Organization ID: $($response.organizationId)"
```

**Expected Response:**
```json
{
  "authUrl": "https://appcenter.intuit.com/connect/oauth2?...",
  "message": "Redirect user to authUrl to authorize",
  "organizationId": "1"
}
```

---

### **Step 3: Check QuickBooks Status**

```powershell
$response = Invoke-RestMethod `
    -Uri "http://localhost:8095/api/quickbooks/status" `
    -Headers $headers

Write-Host "Connected: $($response.connected)"
Write-Host "Organization ID: $($response.organizationId)"
```

---

### **Step 4: Test with Swagger UI**

1. **Open Swagger:** http://localhost:8095/swagger-ui.html

2. **Click "Authorize" button** (top right)

3. **Enter JWT Token:**
   ```
   Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
   ```

4. **Try Endpoints:**
   - No need to enter organizationId parameter
   - It's extracted from JWT automatically!

---

## 🔍 **HOW IT WORKS BEHIND THE SCENES**

### **Request Flow:**

```
┌─────────────────────────────────────────────────────────┐
│  CLIENT                                                 │
│  POST /api/quickbooks/connect                           │
│  Authorization: Bearer eyJhbGc...                       │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  SPRING SECURITY                                        │
│  - CORS Filter                                          │
│  - JwtTenantFilter ← EXTRACTS organizationId            │
│  - Other filters                                        │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  JWT TENANT FILTER                                      │
│                                                         │
│  1. Read Authorization header                           │
│  2. Extract token: eyJhbGc...                           │
│  3. Parse JWT token                                     │
│  4. Get claim: organizationId = 1                       │
│  5. Store: TenantContext.set(1)                         │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  QUICKBOOKS AUTH CONTROLLER                             │
│                                                         │
│  @GetMapping("/connect")                                │
│  public ResponseEntity<?> initiateConnection() {        │
│      Long orgId = TenantContext.require(); ← Gets 1     │
│      // Use orgId                                       │
│  }                                                      │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  QUICKBOOKS OAUTH SERVICE                               │
│  - Uses organization ID: 1                              │
│  - Generates auth URL                                   │
│  - Returns response                                     │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  RESPONSE TO CLIENT                                     │
│  {                                                      │
│    "authUrl": "https://...",                            │
│    "organizationId": "1"                                │
│  }                                                      │
└─────────────────────────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  JWT TENANT FILTER (Finally block)                     │
│  TenantContext.clear() ← Cleanup                        │
└─────────────────────────────────────────────────────────┘
```

---

## 🛡️ **SECURITY BENEFITS**

### **1. Automatic Tenant Isolation** ✅
- Organization ID always from authenticated JWT
- Cannot forge or manipulate organization ID
- No URL parameter injection

### **2. Cleaner API** ✅
- No repetitive `?organizationId=X` in every request
- Follows REST best practices
- Consistent with organization service

### **3. Thread Safety** ✅
- ThreadLocal ensures organization ID scoped to request thread
- No cross-contamination between requests
- Automatic cleanup

---

## ⚠️ **IMPORTANT NOTES**

### **JWT Token MUST Contain organizationId Claim**

Your JWT token from organization service must include:
```json
{
  "sub": "user@example.com",
  "organizationId": 1,    ← REQUIRED
  "roles": ["ADMIN"],
  "exp": 1234567890
}
```

**If missing:**
```
❌ Error: Organization ID not found in request context
```

---

### **Endpoints That Don't Need JWT**

These endpoints still work without JWT:
- ✅ `/api/quickbooks/callback` - OAuth callback from QuickBooks
- ✅ `/api/health` - Health check
- ✅ `/swagger-ui/**` - API documentation
- ✅ `/v3/api-docs/**` - OpenAPI spec

---

## 🔧 **TROUBLESHOOTING**

### **Problem 1: "Organization ID not found in request context"**

**Cause:** JWT token missing `organizationId` claim

**Solution:** Check your organization service JWT generation:
```java
// In organization service JwtUtil or similar
Claims claims = Jwts.claims().setSubject(username);
claims.put("organizationId", user.getOrganizationId());  ← ADD THIS
```

---

### **Problem 2: "Failed to parse JWT token"**

**Cause:** Invalid JWT format or expired token

**Solution:**
- Get new token from `/api/auth/login`
- Check token format: `Authorization: Bearer {token}`
- Verify token not expired

---

### **Problem 3: "Failed to fetch" in Swagger**

**Cause:** CORS or service not running

**Solution:**
```powershell
# 1. Check service is running
curl http://localhost:8095/api/health

# 2. Rebuild and restart
cd C:\Users\Jay\Desktop\aforo_workspace\quickbooks_integration
mvn clean install
mvn spring-boot:run
```

---

## 📊 **COMPARISON**

### **OLD API (Before)**
```bash
# Connect QuickBooks
curl -X GET "http://localhost:8095/api/quickbooks/connect?organizationId=3"

# Check status
curl -X GET "http://localhost:8095/api/quickbooks/status?organizationId=3"

# Disconnect
curl -X POST "http://localhost:8095/api/quickbooks/disconnect?organizationId=3"
```

**Problems:**
- ❌ Repetitive organizationId parameter
- ❌ Can be manipulated in URL
- ❌ Not following REST standards
- ❌ Security risk

---

### **NEW API (Now)**
```bash
# Connect QuickBooks
curl -X GET "http://localhost:8095/api/quickbooks/connect" \
  -H "Authorization: Bearer eyJhbGc..."

# Check status
curl -X GET "http://localhost:8095/api/quickbooks/status" \
  -H "Authorization: Bearer eyJhbGc..."

# Disconnect
curl -X POST "http://localhost:8095/api/quickbooks/disconnect" \
  -H "Authorization: Bearer eyJhbGc..."
```

**Benefits:**
- ✅ Clean URLs
- ✅ Secure (organizationId from JWT)
- ✅ RESTful design
- ✅ Cannot manipulate tenant ID
- ✅ Consistent with organization service

---

## ✅ **SUMMARY**

### **What Changed:**
1. ✅ Added `TenantContext` for storing organization ID
2. ✅ Added `JwtTenantFilter` for automatic JWT parsing
3. ✅ Removed `organizationId` parameters from all endpoints
4. ✅ Organization ID now extracted from JWT token automatically
5. ✅ Updated security configuration
6. ✅ Added nimbus-jose-jwt dependency

### **What You Need:**
1. ✅ JWT token from organization service login
2. ✅ Token must contain `organizationId` claim
3. ✅ Include `Authorization: Bearer {token}` header in all requests

### **Benefits:**
- 🔒 More secure (no URL manipulation)
- 🎯 Cleaner API (no repetitive parameters)
- 🚀 RESTful design
- ⚡ Automatic tenant isolation
- 💪 Thread-safe

---

## 🚀 **NEXT STEPS**

1. **Rebuild QuickBooks Integration Service:**
   ```powershell
   cd C:\Users\Jay\Desktop\aforo_workspace\quickbooks_integration
   mvn clean install
   mvn spring-boot:run
   ```

2. **Get JWT Token from Organization Service:**
   ```powershell
   # Login to get token
   POST http://localhost:8081/api/auth/login
   ```

3. **Test QuickBooks APIs with JWT:**
   ```powershell
   GET http://localhost:8095/api/quickbooks/connect
   Authorization: Bearer {YOUR_TOKEN}
   ```

4. **Verify in Swagger:**
   - Open http://localhost:8095/swagger-ui.html
   - Click "Authorize"
   - Enter: `Bearer {YOUR_TOKEN}`
   - Test endpoints

---

**Your QuickBooks Integration now has proper JWT authentication!** 🎉
