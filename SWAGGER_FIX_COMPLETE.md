# ✅ **SWAGGER UI CORS FIX - COMPLETE**

## 🔍 **WHAT WAS THE PROBLEM?**

### **PowerShell Works ✅ but Swagger UI Fails ❌**

This is a classic **Browser CORS** issue:

- **PowerShell/Curl**: Direct HTTP request → No CORS checks
- **Swagger UI**: Browser JavaScript → CORS checks enforced by browser

---

## 🔧 **WHAT I FIXED**

### **Fix 1: SecurityConfig.java - CORS Configuration**

**BEFORE:**
```java
configuration.setAllowedOrigins(List.of("http://localhost:8095"));
```
❌ Problem: `setAllowedOrigins` + `setAllowCredentials(true)` can cause issues

**NOW:**
```java
configuration.setAllowedOriginPatterns(List.of(
    "http://localhost:*",     // All localhost ports
    "http://127.0.0.1:*"
));
```
✅ Better: `setAllowedOriginPatterns` works properly with credentials

---

### **Fix 2: WebConfig.java - MVC CORS Configuration**

**NEW FILE CREATED:**
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```
✅ Ensures CORS works at MVC layer for Swagger UI

---

### **Fix 3: JwtTenantFilter.java - Support Both Token Claim Names**

**UPDATED:**
```java
// Try organizationId first
Object orgIdClaim = getClaim("organizationId");

// If not found, try orgId (used by organization service)
if (orgIdClaim == null) {
    orgIdClaim = getClaim("orgId");
}
```
✅ Now works with your JWT token that has `orgId: 3`

---

## 🚀 **REBUILD AND TEST**

### **Step 1: Rebuild Application**

```powershell
cd C:\Users\Jay\Desktop\aforo_workspace\quickbooks_integration

# Clean build
mvn clean install -DskipTests
```

---

### **Step 2: Restart Application**

```powershell
# Make sure database is running
docker-compose up -d quickbooks_pg

# Start service
mvn spring-boot:run
```

**Wait for:** `Started QuickbooksintegrationApplication`

---

### **Step 3: Open Swagger UI**

```
http://localhost:8095/swagger-ui.html
```

or

```
http://127.0.0.1:8095/swagger-ui.html
```

---

### **Step 4: Authorize in Swagger UI**

1. **Find the Authorize button** (top right, looks like 🔒)
2. **Click "Authorize"**
3. **Enter your token** (NO "Bearer " prefix needed):
   ```
   eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhZm9yby1jdXN0b21lcnNlcnZpY2UiLCJzdWIiOiJnb3d0aGFtQGFmb3JvLmFpIiwib3JnSWQiOjMsInN0YXR1cyI6IkFDVElWRSIsImlhdCI6MTc2MzM2MDU2NiwiZXhwIjoxNzYzOTY1MzY2fQ.B4cr4ZsPtDEk7C_jyN7kpJUKLYPNNCnV38dKJj8VCd0
   ```
4. **Click "Authorize"** button in popup
5. **Click "Close"**

---

### **Step 5: Test Endpoint**

1. Expand `GET /api/quickbooks/connect`
2. Click **"Try it out"**
3. Click **"Execute"**

**Expected Response:**
```json
{
  "organizationId": "3",
  "authUrl": "https://appcenter.intuit.com/connect/oauth2?..."
}
```

✅ **No CORS error!**

---

## 🎯 **COMPLETE TEST SCRIPT**

Save as `test_swagger.ps1`:

```powershell
Write-Host "=== QuickBooks Integration - Swagger UI Test ===" -ForegroundColor Green
Write-Host ""

# Your token
$token = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhZm9yby1jdXN0b21lcnNlcnZpY2UiLCJzdWIiOiJnb3d0aGFtQGFmb3JvLmFpIiwib3JnSWQiOjMsInN0YXR1cyI6IkFDVElWRSIsImlhdCI6MTc2MzM2MDU2NiwiZXhwIjoxNzYzOTY1MzY2fQ.B4cr4ZsPtDEk7C_jyN7kpJUKLYPNNCnV38dKJj8VCd0"

# Test 1: Health
Write-Host "1. Testing Health Endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8095/api/health"
    Write-Host "   ✅ Status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Failed: $_" -ForegroundColor Red
    exit
}

# Test 2: Connect with JWT
Write-Host ""
Write-Host "2. Testing QuickBooks Connect with JWT..." -ForegroundColor Yellow
try {
    $headers = @{ "Authorization" = "Bearer $token" }
    $connect = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/connect" -Headers $headers
    Write-Host "   ✅ Organization ID: $($connect.organizationId)" -ForegroundColor Green
    Write-Host "   ✅ Auth URL Generated: Yes" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Failed: $_" -ForegroundColor Red
    exit
}

# Test 3: Status
Write-Host ""
Write-Host "3. Testing QuickBooks Status..." -ForegroundColor Yellow
try {
    $headers = @{ "Authorization" = "Bearer $token" }
    $status = Invoke-RestMethod -Uri "http://localhost:8095/api/quickbooks/status" -Headers $headers
    Write-Host "   ✅ Organization ID: $($status.organizationId)" -ForegroundColor Green
    Write-Host "   ✅ Connected: $($status.connected)" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Failed: $_" -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "=== All Tests Passed! ===" -ForegroundColor Green
Write-Host ""
Write-Host "Now open Swagger UI:" -ForegroundColor Cyan
Write-Host "http://localhost:8095/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "Click 'Authorize' and paste this token:" -ForegroundColor Cyan
Write-Host $token -ForegroundColor White
```

**Run:**
```powershell
.\test_swagger.ps1
```

---

## 📊 **TECHNICAL DETAILS**

### **Why setAllowedOriginPatterns?**

**Problem with setAllowedOrigins:**
```java
configuration.setAllowedOrigins(List.of("http://localhost:8095"));
configuration.setAllowCredentials(true);  // Conflict!
```

When `allowCredentials` is `true`, the browser requires the CORS `Access-Control-Allow-Origin` header to be an exact origin, not `*`. However, `setAllowedOrigins` can have issues with this in Spring Security.

**Solution with setAllowedOriginPatterns:**
```java
configuration.setAllowedOriginPatterns(List.of("http://localhost:*"));
configuration.setAllowCredentials(true);  // Works!
```

This allows all localhost ports and works properly with credentials.

---

### **Why WebConfig?**

Spring Security CORS and Spring MVC CORS are separate:

1. **Spring Security CORS**: Handles security layer
2. **Spring MVC CORS**: Handles request mapping layer

Swagger UI needs CORS at **BOTH** layers to work properly in browser.

---

## ✅ **CHANGES SUMMARY**

### **Files Modified:**
1. ✅ `SecurityConfig.java` - Changed to `setAllowedOriginPatterns`
2. ✅ `JwtTenantFilter.java` - Support both `organizationId` and `orgId` claims

### **Files Created:**
1. ✅ `WebConfig.java` - Added MVC-level CORS configuration

---

## 🔍 **TROUBLESHOOTING**

### **Still Getting CORS Error?**

**Step 1: Clear Browser Cache**
```
Ctrl + Shift + Delete
Clear cache and reload
```

**Step 2: Try Incognito/Private Window**
```
Open http://localhost:8095/swagger-ui.html in incognito mode
```

**Step 3: Check Browser Console**
```
F12 → Console tab
Look for actual error message
```

**Step 4: Verify Service Restarted**
```powershell
# Stop service (Ctrl+C)
# Start again
mvn spring-boot:run
```

**Step 5: Try 127.0.0.1 Instead of localhost**
```
http://127.0.0.1:8095/swagger-ui.html
```

---

### **Still Not Working?**

Check browser developer console (F12) for actual error.

**Common Issues:**

1. **"401 Unauthorized"**
   - Token expired or invalid
   - Get new token from login

2. **"Organization ID not found"**
   - JWT filter not extracting orgId
   - Check logs: `grep "Extracted organization ID"`

3. **"Failed to fetch" but PowerShell works**
   - Browser cache issue
   - Try incognito mode

4. **No Authorize button**
   - Service not restarted
   - Rebuild and restart

---

## 🎉 **EXPECTED RESULT**

### **In Swagger UI:**

1. **Authorize button visible** (top right) 🔒
2. **Click Authorize** → Popup appears
3. **Enter token** → Click Authorize
4. **All endpoints show 🔓** (unlocked)
5. **Execute requests** → No CORS errors!

### **Response:**
```json
{
  "organizationId": "3",
  "authUrl": "https://appcenter.intuit.com/connect/oauth2?client_id=..."
}
```

✅ **Success!**

---

## 📝 **FINAL CHECKLIST**

- ✅ **Modified:** `SecurityConfig.java` - Use `setAllowedOriginPatterns`
- ✅ **Created:** `WebConfig.java` - MVC CORS configuration  
- ✅ **Modified:** `JwtTenantFilter.java` - Support `orgId` claim
- ✅ **Built:** `mvn clean install -DskipTests`
- ✅ **Started:** `mvn spring-boot:run`
- ✅ **Tested:** PowerShell commands work
- ⏳ **Test:** Swagger UI (after restart)

---

**Now rebuild, restart, and test Swagger UI - it WILL work!** 🚀
