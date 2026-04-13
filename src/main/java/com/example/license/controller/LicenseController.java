package com.example.license.controller;

import com.example.license.mapper.LicenseMapper;
import com.example.license.model.AuditLog;
import com.example.license.model.LicenseClaims;
import com.example.license.model.LicenseInfo;
import com.example.license.service.LicenseService;
import com.example.license.service.LicenseSigningService;
import com.example.license.service.LicenseSigningService.LicenseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/license")
public class LicenseController {

    private static final Logger log = LoggerFactory.getLogger(LicenseController.class);

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private LicenseSigningService signingService;

    @Autowired(required = false)
    private LicenseMapper licenseMapper;

    // ---- GET /api/license/{tenantId} ----
    @GetMapping("/{tenantId}")
    public ResponseEntity<Map<String, Object>> getLicense(@PathVariable String tenantId) {
        try {
            LicenseInfo info = licenseService.getCurrentLicense(tenantId);
            licenseService.maskLicenseKey(info);
            return ok(info);
        } catch (Exception e) {
            log.error("Failed to get license for tenant={}: {}", tenantId, e.getMessage());
            return error(500, "Failed to retrieve license: " + e.getMessage());
        }
    }

    // ---- GET /api/license/{tenantId}/full-key ----
    // NOTE: This endpoint returns the unmasked license key. It MUST be protected
    // by an authentication/authorization layer (e.g., Spring Security, API gateway)
    // in production deployments. Never expose it to end users directly.
    @GetMapping("/{tenantId}/full-key")
    public ResponseEntity<Map<String, Object>> getFullKey(@PathVariable String tenantId) {
        try {
            LicenseInfo info = licenseService.getCurrentLicense(tenantId);
            // Return full key without masking — caller must be authorized
            return ok(info);
        } catch (Exception e) {
            log.error("Failed to get full license key for tenant={}: {}", tenantId, e.getMessage());
            return error(500, "Failed to retrieve license key: " + e.getMessage());
        }
    }

    // ---- PUT /api/license/{tenantId} ----
    @PutMapping("/{tenantId}")
    public ResponseEntity<Map<String, Object>> updateLicense(
            @PathVariable String tenantId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String newKey = body.get("licenseKey");
        String operatorId = body.getOrDefault("operatorId", "UNKNOWN");
        String operatorIp = getClientIp(request);
        try {
            LicenseInfo info = licenseService.updateLicense(tenantId, newKey, operatorId, operatorIp);
            return ok(info);
        } catch (LicenseException e) {
            log.warn("License update failed for tenant={}: {}", tenantId, e.getMessage());
            return error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error updating license for tenant={}: {}", tenantId, e.getMessage());
            return error(500, "Internal error: " + e.getMessage());
        }
    }

    // ---- POST /api/license/{tenantId}/verify ----
    @PostMapping("/{tenantId}/verify")
    public ResponseEntity<Map<String, Object>> verifyLicense(@PathVariable String tenantId) {
        try {
            LicenseInfo info = licenseService.verifyCurrentLicense(tenantId);
            return ok(info);
        } catch (Exception e) {
            log.error("Verification failed for tenant={}: {}", tenantId, e.getMessage());
            return error(500, "Verification failed: " + e.getMessage());
        }
    }

    // ---- POST /api/license/{tenantId}/revoke ----
    @PostMapping("/{tenantId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeLicense(
            @PathVariable String tenantId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String reason = body.getOrDefault("reason", "No reason provided");
        String operatorId = body.getOrDefault("operatorId", "UNKNOWN");
        String operatorIp = getClientIp(request);
        try {
            licenseService.revokeLicense(tenantId, reason, operatorId, operatorIp);
            return ok("License revoked successfully");
        } catch (LicenseException e) {
            return error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Revocation failed for tenant={}: {}", tenantId, e.getMessage());
            return error(500, "Internal error: " + e.getMessage());
        }
    }

    // ---- GET /api/license/{tenantId}/audit ----
    @GetMapping("/{tenantId}/audit")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            List<AuditLog> logs = licenseService.getAuditLogs(tenantId, page, Math.min(pageSize, 100));
            return ok(logs);
        } catch (Exception e) {
            log.error("Failed to get audit logs for tenant={}: {}", tenantId, e.getMessage());
            return error(500, "Failed to retrieve audit logs: " + e.getMessage());
        }
    }

    /**
     * POST /api/license/generate — signs a LicenseClaims using the server-side demo private key.
     *
     * IMPORTANT: In production this endpoint MUST be removed or moved to an isolated
     * signing service that never shares network access with the application tier.
     * The private key is held server-side (LicenseSigningService.DEMO_PRIVATE_KEY) and
     * is NEVER accepted from the request body.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateLicense(
            @RequestBody Map<String, Object> body) {
        try {
            LicenseClaims claims = buildClaimsFromRequest(body);
            // Private key is always held server-side; it must never come from the request.
            String token = signingService.signWithDemoKey(claims);
            Map<String, Object> result = new HashMap<>();
            result.put("licenseKey", token);
            result.put("digest", signingService.getTokenDigest(token));
            result.put("claims", claims);
            return ok(result);
        } catch (LicenseException e) {
            return error(400, e.getMessage());
        } catch (Exception e) {
            log.error("License generation failed: {}", e.getMessage());
            return error(500, "License generation failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private LicenseClaims buildClaimsFromRequest(Map<String, Object> body) {
        LicenseClaims claims = new LicenseClaims();
        claims.setLicenseId(getStr(body, "licenseId", java.util.UUID.randomUUID().toString()));
        claims.setTenantId(getStr(body, "tenantId", "DEFAULT"));
        claims.setTenantName(getStr(body, "tenantName", "Default Tenant"));
        claims.setLicenseType(getStr(body, "licenseType", "TRIAL"));
        claims.setIssuedAt(System.currentTimeMillis() / 1000L);
        Object exp = body.get("expiresAt");
        claims.setExpiresAt(exp != null ? ((Number) exp).longValue()
                : (System.currentTimeMillis() / 1000L + 90L * 86400L));
        claims.setGracePeriodDays(getLong(body, "gracePeriodDays", 7L));
        claims.setMaxUsers(getInt(body, "maxUsers", 10));
        claims.setMaxConcurrent(getInt(body, "maxConcurrent", 5));
        claims.setMaxTasks(getInt(body, "maxTasks", 1000));
        claims.setMaxDataSources(getInt(body, "maxDataSources", 10));
        claims.setMaxSdkInstances(getInt(body, "maxSdkInstances", 3));
        Object features = body.get("features");
        if (features instanceof List) {
            claims.setFeatures((List<String>) features);
        }
        claims.setEnvironment(getStr(body, "environment", "PRODUCTION"));
        claims.setHardLimit(Boolean.TRUE.equals(body.get("hardLimit")));
        claims.setRevoked(false);
        claims.setIssuer(getStr(body, "issuer", "com.example.license"));
        return claims;
    }

    private String getStr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return (v instanceof String && !((String) v).isBlank()) ? (String) v : def;
    }

    private int getInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return (v instanceof Number) ? ((Number) v).intValue() : def;
    }

    private long getLong(Map<String, Object> m, String key, long def) {
        Object v = m.get(key);
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        resp.put("data", data);
        return ResponseEntity.ok(resp);
    }

    private ResponseEntity<Map<String, Object>> error(int code, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", code);
        resp.put("message", message);
        resp.put("data", null);
        return ResponseEntity.status(code >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST)
                .body(resp);
    }
}
