package com.example.license.service;

import com.example.license.model.LicenseAuditLog;
import com.example.license.model.LicenseStatusInfo;

import java.util.List;

/**
 * License 管理服务接口。
 */
public interface LicenseService {

    /**
     * 查询当前 License 状态（含 claims 摘要）。
     *
     * @param maskedKey 是否对 License Key 脱敏
     * @return LicenseStatusInfo
     */
    LicenseStatusInfo getStatus(boolean maskedKey);

    /**
     * 预校验 License Key（不持久化，不写审计日志）。
     *
     * @param licenseKey 待校验的 License Key
     * @return 校验结果
     */
    LicenseStatusInfo preValidate(String licenseKey);

    /**
     * 更新 License Key（覆盖续期/扩容）。
     * 先验签，成功后持久化并写入审计日志。
     *
     * @param licenseKey 新 License Key
     * @param operator   操作人
     * @param operatorIp 操作 IP
     * @return 更新后的状态
     */
    LicenseStatusInfo update(String licenseKey, String operator, String operatorIp);

    /**
     * 查询最近 N 条审计日志。
     */
    List<LicenseAuditLog> getAuditLogs(int limit);
}
