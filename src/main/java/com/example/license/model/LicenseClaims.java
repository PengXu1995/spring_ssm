package com.example.license.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseClaims {

    private String licenseId;
    private String tenantId;
    private String tenantName;
    private String licenseType;
    private long issuedAt;
    private long expiresAt;
    private long gracePeriodDays;
    private int maxUsers;
    private int maxConcurrent;
    private int maxTasks;
    private int maxDataSources;
    private int maxSdkInstances;
    private List<String> features;
    private String environment;
    private List<String> allowedDomains;
    private String hostId;
    private boolean hardLimit;
    private boolean revoked;
    private String revokedReason;
    private String issuer;

    public LicenseClaims() {}

    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }

    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public long getGracePeriodDays() { return gracePeriodDays; }
    public void setGracePeriodDays(long gracePeriodDays) { this.gracePeriodDays = gracePeriodDays; }

    public int getMaxUsers() { return maxUsers; }
    public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }

    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }

    public int getMaxTasks() { return maxTasks; }
    public void setMaxTasks(int maxTasks) { this.maxTasks = maxTasks; }

    public int getMaxDataSources() { return maxDataSources; }
    public void setMaxDataSources(int maxDataSources) { this.maxDataSources = maxDataSources; }

    public int getMaxSdkInstances() { return maxSdkInstances; }
    public void setMaxSdkInstances(int maxSdkInstances) { this.maxSdkInstances = maxSdkInstances; }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public List<String> getAllowedDomains() { return allowedDomains; }
    public void setAllowedDomains(List<String> allowedDomains) { this.allowedDomains = allowedDomains; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public boolean isHardLimit() { return hardLimit; }
    public void setHardLimit(boolean hardLimit) { this.hardLimit = hardLimit; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
