package com.example.license.model;

/**
 * License 校验状态枚举。
 */
public enum LicenseStatus {

    /** 有效，未过期且未触发任何告警 */
    VALID,

    /** 即将过期（在宽限期内或距过期 <= 7 天） */
    EXPIRING_SOON,

    /** 已过期（超出宽限期） */
    EXPIRED,

    /** 校验失败（签名无效、格式错误、租户不匹配等） */
    INVALID,

    /** 疑似时间回拨篡改 */
    TIME_TAMPERED,

    /** 尚未配置 License */
    NOT_CONFIGURED
}
