package com.example.license.service.impl;

import com.example.license.mapper.LicenseAuditLogMapper;
import com.example.license.model.LicenseAuditLog;
import com.example.license.model.LicenseClaims;
import com.example.license.model.LicenseStatus;
import com.example.license.model.LicenseStatusInfo;
import com.example.license.service.LicenseService;
import com.example.license.util.LicenseCrypto;
import com.example.license.util.LicenseValidator;
import com.example.license.util.LicenseVerifyException;
import com.example.license.util.TimeGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * License 管理服务实现。
 * <p>
 * 存储策略：License Key 持久化至 license_info 表（单行，id=1）。
 * 公钥从 classpath:license_public.pem 加载，私钥仅在签发侧使用。
 */
@Service
public class LicenseServiceImpl implements LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseServiceImpl.class);

    @Value("classpath:license_public.pem")
    private Resource publicKeyResource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LicenseAuditLogMapper auditLogMapper;

    private byte[] publicKeyBytes;
    private final TimeGuard timeGuard = new TimeGuard();

    @PostConstruct
    public void init() throws Exception {
        try (InputStream is = publicKeyResource.getInputStream()) {
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            publicKeyBytes = LicenseCrypto.pemToBytes(pem);
            log.info("License 公钥加载成功");
        }
    }

    @Override
    public LicenseStatusInfo getStatus(boolean maskedKey) {
        String licenseKey = loadCurrentLicenseKey();
        if (licenseKey == null || licenseKey.isBlank()) {
            return LicenseStatusInfo.ofError(LicenseStatus.NOT_CONFIGURED, "尚未配置 License");
        }

        LicenseStatusInfo info = doValidate(licenseKey);

        if (maskedKey) {
            info.setMaskedLicenseKey(LicenseCrypto.maskLicenseKey(licenseKey));
        } else {
            info.setMaskedLicenseKey(licenseKey);
        }

        // 更新 lastSeen（若校验不是时间回拨则更新）
        if (info.getStatus() != LicenseStatus.TIME_TAMPERED) {
            long nowSecs = System.currentTimeMillis() / 1000;
            timeGuard.updateLastSeen(nowSecs);
            saveLastValidated(nowSecs);
        }

        return info;
    }

    @Override
    public LicenseStatusInfo preValidate(String licenseKey) {
        return doValidate(licenseKey);
    }

    @Override
    @Transactional
    public LicenseStatusInfo update(String licenseKey, String operator, String operatorIp) {
        String oldLicenseId = null;
        try {
            // 读取旧 license ID（用于审计）
            String oldKey = loadCurrentLicenseKey();
            if (oldKey != null && !oldKey.isBlank()) {
                try {
                    LicenseClaims oldClaims = LicenseCrypto.verify(oldKey, publicKeyBytes);
                    oldLicenseId = oldClaims.getLicenseId();
                } catch (LicenseVerifyException ignored) {
                    // 旧 license 可能本就无效
                }
            }

            // 验签新 license
            LicenseClaims newClaims = LicenseCrypto.verify(licenseKey, publicKeyBytes);

            // 有效性校验
            long nowSecs = System.currentTimeMillis() / 1000;
            long lastSeen = timeGuard.readLastSeen();
            LicenseStatusInfo validationResult = LicenseValidator.validate(newClaims, nowSecs, lastSeen, -1, -1);

            if (validationResult.getStatus() == LicenseStatus.EXPIRED
                    || validationResult.getStatus() == LicenseStatus.INVALID
                    || validationResult.getStatus() == LicenseStatus.TIME_TAMPERED) {
                writeAuditLog(operator, operatorIp, oldLicenseId, newClaims.getLicenseId(),
                        "UPDATE", "FAILURE", validationResult.getErrorReason());
                return validationResult;
            }

            // 持久化
            saveOrUpdateLicenseKey(licenseKey);
            timeGuard.updateLastSeen(nowSecs);
            saveLastValidated(nowSecs);

            writeAuditLog(operator, operatorIp, oldLicenseId, newClaims.getLicenseId(),
                    "UPDATE", "SUCCESS", null);

            validationResult.setMaskedLicenseKey(LicenseCrypto.maskLicenseKey(licenseKey));
            return validationResult;

        } catch (LicenseVerifyException e) {
            log.warn("License 更新失败（验签异常）：{}", e.getMessage());
            writeAuditLog(operator, operatorIp, oldLicenseId, null,
                    "UPDATE", "FAILURE", e.getMessage());
            return LicenseStatusInfo.ofError(LicenseStatus.INVALID, e.getMessage());
        }
    }

    @Override
    public List<LicenseAuditLog> getAuditLogs(int limit) {
        return auditLogMapper.findRecent(limit > 0 ? limit : 20);
    }

    // ---- private helpers ----

    private LicenseStatusInfo doValidate(String licenseKey) {
        LicenseClaims claims;
        try {
            claims = LicenseCrypto.verify(licenseKey, publicKeyBytes);
        } catch (LicenseVerifyException e) {
            log.debug("License 验签失败：{}", e.getMessage());
            return LicenseStatusInfo.ofError(LicenseStatus.INVALID, e.getMessage());
        }

        long nowSecs = System.currentTimeMillis() / 1000;
        long lastSeen = timeGuard.readLastSeen();
        return LicenseValidator.validate(claims, nowSecs, lastSeen, -1, -1);
    }

    private String loadCurrentLicenseKey() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT license_key FROM license_info WHERE id = 1",
                    String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveOrUpdateLicenseKey(String licenseKey) {
        int rows = jdbcTemplate.update(
                "UPDATE license_info SET license_key = ?, updated_at = ? WHERE id = 1",
                licenseKey, System.currentTimeMillis() / 1000);
        if (rows == 0) {
            jdbcTemplate.update(
                    "INSERT INTO license_info (id, license_key, updated_at) VALUES (1, ?, ?)",
                    licenseKey, System.currentTimeMillis() / 1000);
        }
    }

    private void saveLastValidated(long nowSecs) {
        try {
            jdbcTemplate.update(
                    "UPDATE license_info SET last_validated_at = ? WHERE id = 1",
                    nowSecs);
        } catch (Exception e) {
            log.warn("更新 last_validated_at 失败：{}", e.getMessage());
        }
    }

    private void writeAuditLog(String operator, String operatorIp, String oldLicenseId,
                                String newLicenseId, String action, String result, String errorMsg) {
        try {
            LicenseAuditLog auditLog = new LicenseAuditLog();
            auditLog.setOperator(operator != null ? operator : "system");
            auditLog.setOperatorIp(operatorIp != null ? operatorIp : "unknown");
            auditLog.setOldLicenseId(oldLicenseId);
            auditLog.setNewLicenseId(newLicenseId);
            auditLog.setAction(action);
            auditLog.setResult(result);
            auditLog.setErrorMsg(errorMsg);
            auditLog.setCreatedAt(System.currentTimeMillis() / 1000);
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("写入审计日志失败：{}", e.getMessage());
        }
    }
}
