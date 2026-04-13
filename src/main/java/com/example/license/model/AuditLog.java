package com.example.license.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLog {

    private Long id;
    private String tenantId;
    private String action;
    private String operatorId;
    private String operatorIp;
    private String oldLicenseDigest;
    private String newLicenseDigest;
    private String result;
    private String errorMessage;
    private Date createdAt;

    public AuditLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getOperatorIp() { return operatorIp; }
    public void setOperatorIp(String operatorIp) { this.operatorIp = operatorIp; }

    public String getOldLicenseDigest() { return oldLicenseDigest; }
    public void setOldLicenseDigest(String oldLicenseDigest) { this.oldLicenseDigest = oldLicenseDigest; }

    public String getNewLicenseDigest() { return newLicenseDigest; }
    public void setNewLicenseDigest(String newLicenseDigest) { this.newLicenseDigest = newLicenseDigest; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
