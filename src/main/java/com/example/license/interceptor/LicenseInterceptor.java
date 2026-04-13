package com.example.license.interceptor;

import com.example.license.model.LicenseInfo;
import com.example.license.model.LicenseStatus;
import com.example.license.service.LicenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Intercepts all /api/** requests (except license management endpoints).
 * Validates the license and rejects requests if the license is EXPIRED or REVOKED.
 */
@Component
public class LicenseInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LicenseInterceptor.class);
    private static final String DEFAULT_TENANT_HEADER = "X-Tenant-Id";
    private static final String DEFAULT_TENANT_ID = "DEFAULT";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private LicenseService licenseService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String tenantId = request.getHeader(DEFAULT_TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = DEFAULT_TENANT_ID;
        }

        LicenseInfo info;
        try {
            info = licenseService.getCurrentLicense(tenantId);
        } catch (Exception e) {
            log.error("License check failed for tenant={}: {}", tenantId, e.getMessage());
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "License check failed: " + e.getMessage());
            return false;
        }

        LicenseStatus status = info.getStatus();

        if (status == LicenseStatus.REVOKED) {
            writeError(response, 402, "License has been revoked. Please contact support.");
            return false;
        }

        if (status == LicenseStatus.EXPIRED) {
            writeError(response, 402,
                    "License has expired and the grace period has passed. Please renew your license.");
            return false;
        }

        if (status == LicenseStatus.INVALID_SIGNATURE) {
            writeError(response, 402, "License signature is invalid. Please update your license.");
            return false;
        }

        if (status == LicenseStatus.CLOCK_TAMPERED) {
            writeError(response, 402, "System clock appears to have been tampered. Please contact support.");
            return false;
        }

        if (status == LicenseStatus.NOT_CONFIGURED) {
            writeError(response, 402, "No license configured. Please install a valid license.");
            return false;
        }

        if (status == LicenseStatus.EXPIRING_SOON || status == LicenseStatus.IN_GRACE_PERIOD) {
            response.setHeader("X-License-Warning", info.getStatusMessage());
            response.setHeader("X-License-Days-Until-Expiry",
                    String.valueOf(info.getDaysUntilExpiry()));
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", statusCode);
        body.put("message", message);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
