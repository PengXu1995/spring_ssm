package com.example.license.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseInfo {

    private String licenseKey;
    private LicenseClaims claims;
    private LicenseStatus status;
    private String statusMessage;
    private long daysUntilExpiry;
    private String lastVerifiedAt;
    private String lastError;
    private String updatedBy;
    private String updatedAt;
    private String updaterIp;

    public LicenseInfo() {}

    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }

    public LicenseClaims getClaims() { return claims; }
    public void setClaims(LicenseClaims claims) { this.claims = claims; }

    public LicenseStatus getStatus() { return status; }
    public void setStatus(LicenseStatus status) { this.status = status; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public long getDaysUntilExpiry() { return daysUntilExpiry; }
    public void setDaysUntilExpiry(long daysUntilExpiry) { this.daysUntilExpiry = daysUntilExpiry; }

    public String getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(String lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdaterIp() { return updaterIp; }
    public void setUpdaterIp(String updaterIp) { this.updaterIp = updaterIp; }
}
