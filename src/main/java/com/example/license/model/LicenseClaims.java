package com.example.license.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * License 声明（claims）数据结构。
 * 格式：base64url(claims_json) + "." + base64url(rsa_sha256_signature)
 * 签名覆盖 base64url(claims_json) 部分（不含 signature 字段本身）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LicenseClaims {

    /** 唯一 license 编号 */
    @JsonProperty("licenseId")
    private String licenseId;

    /** 绑定租户 ID */
    @JsonProperty("tenantId")
    private String tenantId;

    /** 客户名称（展示用） */
    @JsonProperty("customerName")
    private String customerName;

    /** 授权方案：trial / standard / enterprise */
    @JsonProperty("plan")
    private String plan;

    /** 签发时间（Unix 秒） */
    @JsonProperty("issueAt")
    private long issueAt;

    /** 过期时间（Unix 秒） */
    @JsonProperty("expireAt")
    private long expireAt;

    /** 宽限期（天），到期后该天数内仍可用但会告警 */
    @JsonProperty("gracePeriodDays")
    private int gracePeriodDays;

    /** 签发方标识 */
    @JsonProperty("issuer")
    private String issuer;

    /** 最大用户数（-1 表示不限） */
    @JsonProperty("maxUsers")
    private int maxUsers;

    /** 最大 SDK 实例数（-1 表示不限） */
    @JsonProperty("maxSdkInstances")
    private int maxSdkInstances;

    /** 最大每日处理量（条，-1 表示不限） */
    @JsonProperty("maxDailyRecords")
    private long maxDailyRecords;

    /** 授权功能列表（模块名称） */
    @JsonProperty("features")
    private List<String> features;

    // ---- getters & setters ----

    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public long getIssueAt() { return issueAt; }
    public void setIssueAt(long issueAt) { this.issueAt = issueAt; }

    public long getExpireAt() { return expireAt; }
    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }

    public int getGracePeriodDays() { return gracePeriodDays; }
    public void setGracePeriodDays(int gracePeriodDays) { this.gracePeriodDays = gracePeriodDays; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public int getMaxUsers() { return maxUsers; }
    public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }

    public int getMaxSdkInstances() { return maxSdkInstances; }
    public void setMaxSdkInstances(int maxSdkInstances) { this.maxSdkInstances = maxSdkInstances; }

    public long getMaxDailyRecords() { return maxDailyRecords; }
    public void setMaxDailyRecords(long maxDailyRecords) { this.maxDailyRecords = maxDailyRecords; }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
}
