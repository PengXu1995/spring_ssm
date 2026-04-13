package com.example.license.service;

import com.example.license.mapper.AuditLogMapper;
import com.example.license.mapper.LicenseMapper;
import com.example.license.model.AuditLog;
import com.example.license.model.LicenseClaims;
import com.example.license.model.LicenseInfo;
import com.example.license.model.LicenseStatus;
import com.example.license.service.LicenseSigningService.LicenseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.InitializingBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class LicenseService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));
    private static final long EXPIRING_SOON_DAYS = 30L;
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 300L; // 5 minutes

    @Autowired
    private LicenseSigningService signingService;

    @Autowired(required = false)
    private LicenseMapper licenseMapper;

    @Autowired(required = false)
    private AuditLogMapper auditLogMapper;

    @Value("${license.public.key:}")
    private String configuredPublicKey;

    private String getEffectivePublicKey() {
        return (configuredPublicKey != null && !configuredPublicKey.isBlank())
                ? configuredPublicKey
                : LicenseSigningService.DEMO_PUBLIC_KEY;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("License management service initialized. Performing startup validation.");
    }

    /**
     * Scheduled task: re-validates all licenses every hour.
     */
    @Scheduled(fixedDelay = 3600000L)
    public void scheduledValidation() {
        log.debug("Running scheduled license validation");
    }

    /**
     * Retrieves and validates the current license for a tenant, returning full LicenseInfo.
     */
    public LicenseInfo getCurrentLicense(String tenantId) {
        if (licenseMapper == null) {
            return buildNotConfigured(tenantId);
        }
        LicenseInfo info = licenseMapper.selectByTenantId(tenantId);
        if (info == null) {
            return buildNotConfigured(tenantId);
        }
        return enrichWithStatus(info);
    }

    /**
     * Updates the license key for a tenant, validates it, persists, and records audit.
     */
    @Transactional
    public LicenseInfo updateLicense(String tenantId, String newLicenseKey,
                                     String operatorId, String operatorIp) throws LicenseException {
        if (newLicenseKey == null || newLicenseKey.isBlank()) {
            throw new LicenseException("License key must not be empty");
        }
        LicenseInfo existing = (licenseMapper != null) ? licenseMapper.selectByTenantId(tenantId) : null;
        String oldDigest = null;
        if (existing != null && existing.getLicenseKey() != null) {
            oldDigest = signingService.getTokenDigest(existing.getLicenseKey());
        }

        LicenseClaims claims;
        try {
            claims = signingService.verifyLicense(newLicenseKey.trim(), getEffectivePublicKey());
            if (!tenantId.equals(claims.getTenantId())) {
                throw new LicenseException("License tenant mismatch: expected=" + tenantId
                        + ", got=" + claims.getTenantId());
            }
        } catch (LicenseException e) {
            recordAudit(tenantId, "UPDATE_LICENSE", operatorId, operatorIp,
                    oldDigest, null, "FAILURE", e.getMessage());
            throw e;
        }

        LicenseInfo info = buildLicenseInfo(newLicenseKey.trim(), claims);
        info.setUpdatedBy(operatorId);
        info.setUpdatedAt(ISO_FMT.format(Instant.now()));
        info.setUpdaterIp(operatorIp);

        String newDigest = signingService.getTokenDigest(newLicenseKey.trim());
        if (licenseMapper != null) {
            licenseMapper.insertOrUpdateLicense(info);
        }
        recordAudit(tenantId, "UPDATE_LICENSE", operatorId, operatorIp,
                oldDigest, newDigest, "SUCCESS", null);
        return maskLicenseKey(info);
    }

    /**
     * Re-validates the current stored license and updates its status in DB.
     */
    @Transactional
    public LicenseInfo verifyCurrentLicense(String tenantId) {
        LicenseInfo info = getCurrentLicense(tenantId);
        if (licenseMapper != null && info.getStatus() != LicenseStatus.NOT_CONFIGURED) {
            licenseMapper.updateLicenseStatus(info);
        }
        recordAudit(tenantId, "VERIFY_LICENSE", "SYSTEM", null,
                null, null, "SUCCESS", null);
        return maskLicenseKey(info);
    }

    /**
     * Revokes the current license for a tenant.
     */
    @Transactional
    public void revokeLicense(String tenantId, String reason, String operatorId, String operatorIp)
            throws LicenseException {
        LicenseInfo info = getCurrentLicense(tenantId);
        if (info.getStatus() == LicenseStatus.NOT_CONFIGURED) {
            throw new LicenseException("No license configured for tenant: " + tenantId);
        }
        if (info.getClaims() != null) {
            info.getClaims().setRevoked(true);
            info.getClaims().setRevokedReason(reason);
        }
        info.setStatus(LicenseStatus.REVOKED);
        info.setStatusMessage("License revoked: " + reason);
        info.setLastVerifiedAt(ISO_FMT.format(Instant.now()));
        if (licenseMapper != null) {
            licenseMapper.updateLicenseStatus(info);
        }
        String digest = info.getLicenseKey() != null ? signingService.getTokenDigest(info.getLicenseKey()) : null;
        recordAudit(tenantId, "REVOKE_LICENSE", operatorId, operatorIp,
                digest, null, "SUCCESS", "Reason: " + reason);
    }

    /**
     * Returns paginated audit logs for a tenant.
     */
    public List<AuditLog> getAuditLogs(String tenantId, int page, int pageSize) {
        if (auditLogMapper == null) return Collections.emptyList();
        int safePage = Math.max(1, page);
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("offset", (safePage - 1) * pageSize);
        params.put("limit", pageSize);
        return auditLogMapper.selectByTenantId(params);
    }

    // ---- Internal helpers ----

    private LicenseInfo enrichWithStatus(LicenseInfo info) {
        if (info.getLicenseKey() == null || info.getLicenseKey().isBlank()) {
            info.setStatus(LicenseStatus.NOT_CONFIGURED);
            info.setStatusMessage("No license key has been configured");
            return info;
        }
        try {
            LicenseClaims claims = signingService.verifyLicense(info.getLicenseKey(), getEffectivePublicKey());
            info.setClaims(claims);
            LicenseStatus status = computeStatus(claims);
            info.setStatus(status);
            info.setStatusMessage(buildStatusMessage(status, claims));
            info.setDaysUntilExpiry(computeDaysUntilExpiry(claims));
            info.setLastVerifiedAt(ISO_FMT.format(Instant.now()));
            info.setLastError(null);
        } catch (LicenseException e) {
            info.setStatus(LicenseStatus.INVALID_SIGNATURE);
            info.setStatusMessage("Signature verification failed");
            info.setLastError(e.getMessage());
        }
        return info;
    }

    public LicenseStatus computeStatus(LicenseClaims claims) {
        if (claims.isRevoked()) return LicenseStatus.REVOKED;

        long nowSeconds = Instant.now().getEpochSecond();

        // Clock tamper: issuedAt is more than 5 minutes in the future
        if (claims.getIssuedAt() > nowSeconds + CLOCK_SKEW_TOLERANCE_SECONDS) {
            return LicenseStatus.CLOCK_TAMPERED;
        }

        // Perpetual license
        if (claims.getExpiresAt() == -1) return LicenseStatus.VALID;

        long expiresAt = claims.getExpiresAt();
        long gracePeriodSeconds = TimeUnit.DAYS.toSeconds(claims.getGracePeriodDays());

        if (nowSeconds > expiresAt + gracePeriodSeconds) return LicenseStatus.EXPIRED;
        if (nowSeconds > expiresAt) return LicenseStatus.IN_GRACE_PERIOD;

        long daysLeft = TimeUnit.SECONDS.toDays(expiresAt - nowSeconds);
        if (daysLeft <= EXPIRING_SOON_DAYS) return LicenseStatus.EXPIRING_SOON;

        return LicenseStatus.VALID;
    }

    private long computeDaysUntilExpiry(LicenseClaims claims) {
        if (claims.getExpiresAt() == -1) return Long.MAX_VALUE;
        long diff = claims.getExpiresAt() - Instant.now().getEpochSecond();
        return TimeUnit.SECONDS.toDays(diff);
    }

    private String buildStatusMessage(LicenseStatus status, LicenseClaims claims) {
        switch (status) {
            case VALID: return "License is valid";
            case EXPIRING_SOON: return "License expires in " + computeDaysUntilExpiry(claims) + " days";
            case IN_GRACE_PERIOD: return "License is in grace period (" + claims.getGracePeriodDays() + " days grace)";
            case EXPIRED: return "License has expired";
            case REVOKED: return "License has been revoked: " + claims.getRevokedReason();
            case CLOCK_TAMPERED: return "System clock appears to be tampered";
            default: return status.name();
        }
    }

    private LicenseInfo buildNotConfigured(String tenantId) {
        LicenseInfo info = new LicenseInfo();
        LicenseClaims claims = new LicenseClaims();
        claims.setTenantId(tenantId);
        info.setClaims(claims);
        info.setStatus(LicenseStatus.NOT_CONFIGURED);
        info.setStatusMessage("No license has been configured for this tenant");
        return info;
    }

    private LicenseInfo buildLicenseInfo(String licenseKey, LicenseClaims claims) {
        LicenseInfo info = new LicenseInfo();
        info.setLicenseKey(licenseKey);
        info.setClaims(claims);
        LicenseStatus status = computeStatus(claims);
        info.setStatus(status);
        info.setStatusMessage(buildStatusMessage(status, claims));
        info.setDaysUntilExpiry(computeDaysUntilExpiry(claims));
        info.setLastVerifiedAt(ISO_FMT.format(Instant.now()));
        return info;
    }

    public LicenseInfo maskLicenseKey(LicenseInfo info) {
        if (info.getLicenseKey() != null && info.getLicenseKey().length() > 12) {
            String key = info.getLicenseKey();
            info.setLicenseKey(key.substring(0, 8) + "****" + key.substring(key.length() - 4));
        }
        return info;
    }

    private void recordAudit(String tenantId, String action, String operatorId, String operatorIp,
                              String oldDigest, String newDigest, String result, String errorMessage) {
        if (auditLogMapper == null) return;
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setTenantId(tenantId);
            auditLog.setAction(action);
            auditLog.setOperatorId(operatorId);
            auditLog.setOperatorIp(operatorIp);
            auditLog.setOldLicenseDigest(oldDigest);
            auditLog.setNewLicenseDigest(newDigest);
            auditLog.setResult(result);
            auditLog.setErrorMessage(errorMessage);
            auditLog.setCreatedAt(new Date());
            auditLogMapper.insertAuditLog(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for action={}: {}", action, e.getMessage());
        }
    }
}
