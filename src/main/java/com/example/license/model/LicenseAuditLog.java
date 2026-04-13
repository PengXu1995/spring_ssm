package com.example.license.model;

/**
 * License 更新审计日志实体。
 */
public class LicenseAuditLog {

    private Long id;
    private String operator;
    private String operatorIp;
    private String oldLicenseId;
    private String newLicenseId;
    /** UPDATE / PRE_VALIDATE */
    private String action;
    /** SUCCESS / FAILURE */
    private String result;
    private String errorMsg;
    private long createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getOperatorIp() { return operatorIp; }
    public void setOperatorIp(String operatorIp) { this.operatorIp = operatorIp; }

    public String getOldLicenseId() { return oldLicenseId; }
    public void setOldLicenseId(String oldLicenseId) { this.oldLicenseId = oldLicenseId; }

    public String getNewLicenseId() { return newLicenseId; }
    public void setNewLicenseId(String newLicenseId) { this.newLicenseId = newLicenseId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
