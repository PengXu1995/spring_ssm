package com.example.license.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * License 校验结果，包含状态、错误原因及 claims 摘要。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseStatusInfo {

    private LicenseStatus status;
    private String errorReason;
    private long lastValidatedAt;

    // claims 摘要字段（校验成功时填充）
    private String licenseId;
    private String tenantId;
    private String customerName;
    private String plan;
    private long expireAt;
    private int gracePeriodDays;
    private int maxUsers;
    private int maxSdkInstances;
    private long maxDailyRecords;
    private List<String> features;

    /**
     * License Key 展示值：masked 时为脱敏形式（前4位 + *** + 后4位），
     * full 接口时为明文完整 Key。字段名保持统一以复用同一 DTO。
     */
    private String maskedLicenseKey;

    public static LicenseStatusInfo ofError(LicenseStatus status, String errorReason) {
        LicenseStatusInfo info = new LicenseStatusInfo();
        info.status = status;
        info.errorReason = errorReason;
        info.lastValidatedAt = System.currentTimeMillis() / 1000;
        return info;
    }

    public static LicenseStatusInfo ofClaims(LicenseStatus status, LicenseClaims claims) {
        LicenseStatusInfo info = new LicenseStatusInfo();
        info.status = status;
        info.lastValidatedAt = System.currentTimeMillis() / 1000;
        info.licenseId = claims.getLicenseId();
        info.tenantId = claims.getTenantId();
        info.customerName = claims.getCustomerName();
        info.plan = claims.getPlan();
        info.expireAt = claims.getExpireAt();
        info.gracePeriodDays = claims.getGracePeriodDays();
        info.maxUsers = claims.getMaxUsers();
        info.maxSdkInstances = claims.getMaxSdkInstances();
        info.maxDailyRecords = claims.getMaxDailyRecords();
        info.features = claims.getFeatures();
        return info;
    }

    // ---- getters & setters ----

    public LicenseStatus getStatus() { return status; }
    public void setStatus(LicenseStatus status) { this.status = status; }

    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }

    public long getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(long lastValidatedAt) { this.lastValidatedAt = lastValidatedAt; }

    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public long getExpireAt() { return expireAt; }
    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }

    public int getGracePeriodDays() { return gracePeriodDays; }
    public void setGracePeriodDays(int gracePeriodDays) { this.gracePeriodDays = gracePeriodDays; }

    public int getMaxUsers() { return maxUsers; }
    public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }

    public int getMaxSdkInstances() { return maxSdkInstances; }
    public void setMaxSdkInstances(int maxSdkInstances) { this.maxSdkInstances = maxSdkInstances; }

    public long getMaxDailyRecords() { return maxDailyRecords; }
    public void setMaxDailyRecords(long maxDailyRecords) { this.maxDailyRecords = maxDailyRecords; }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }

    public String getMaskedLicenseKey() { return maskedLicenseKey; }
    public void setMaskedLicenseKey(String maskedLicenseKey) { this.maskedLicenseKey = maskedLicenseKey; }
}
