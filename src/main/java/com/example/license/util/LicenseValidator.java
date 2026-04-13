package com.example.license.util;

import com.example.license.model.LicenseClaims;
import com.example.license.model.LicenseStatus;
import com.example.license.model.LicenseStatusInfo;

import java.util.concurrent.TimeUnit;

/**
 * License 有效性校验器。
 * <p>
 * 校验规则（按优先级）：
 * <ol>
 *   <li>系统时间回拨检测（lastSeen 单调递增）</li>
 *   <li>到期检测（expireAt）</li>
 *   <li>宽限期检测（gracePeriodDays）</li>
 *   <li>即将过期告警（7 天内）</li>
 *   <li>配额校验（预留接口，传入 currentUsers/currentSdk 时生效）</li>
 * </ol>
 */
public class LicenseValidator {

    /** 即将过期告警阈值（秒） */
    private static final long EXPIRING_SOON_THRESHOLD_SECS = TimeUnit.DAYS.toSeconds(7);

    private LicenseValidator() {}

    /**
     * 校验 claims 有效性。
     *
     * @param claims          已通过签名验证的 License 声明
     * @param nowSecs         当前时间（Unix 秒），由调用方注入便于测试
     * @param lastSeenSecs    持久化的最近一次校验时间戳（Unix 秒），用于时间回拨检测；首次传 0
     * @param currentUsers    当前用户数（传 -1 跳过配额校验）
     * @param currentSdk      当前 SDK 实例数（传 -1 跳过配额校验）
     * @return LicenseStatusInfo
     */
    public static LicenseStatusInfo validate(LicenseClaims claims, long nowSecs, long lastSeenSecs,
                                             int currentUsers, int currentSdk) {
        // 1. 时间回拨检测：容许最多 60 秒的时钟抖动
        if (lastSeenSecs > 0 && nowSecs < lastSeenSecs - 60) {
            return LicenseStatusInfo.ofError(LicenseStatus.TIME_TAMPERED,
                    String.format("检测到时间回拨：当前时间 %d < 上次校验时间 %d，疑似系统时钟被篡改",
                            nowSecs, lastSeenSecs));
        }

        // 2. 已过期（超出宽限期）
        long graceEndSecs = claims.getExpireAt() + TimeUnit.DAYS.toSeconds(claims.getGracePeriodDays());
        if (nowSecs > graceEndSecs) {
            return LicenseStatusInfo.ofError(LicenseStatus.EXPIRED,
                    String.format("License 已过期（含 %d 天宽限期），过期时间：%d，当前时间：%d",
                            claims.getGracePeriodDays(), graceEndSecs, nowSecs));
        }

        // 3. 在宽限期内（已到 expireAt 但未超宽限期）
        if (nowSecs > claims.getExpireAt()) {
            LicenseStatusInfo info = LicenseStatusInfo.ofClaims(LicenseStatus.EXPIRING_SOON, claims);
            long remainDays = TimeUnit.SECONDS.toDays(graceEndSecs - nowSecs);
            info.setErrorReason(String.format("License 已到期，处于宽限期内，还剩约 %d 天", remainDays));
            return info;
        }

        // 4. 即将过期（7 天以内）
        if (nowSecs >= claims.getExpireAt() - EXPIRING_SOON_THRESHOLD_SECS) {
            LicenseStatusInfo info = LicenseStatusInfo.ofClaims(LicenseStatus.EXPIRING_SOON, claims);
            long remainDays = TimeUnit.SECONDS.toDays(claims.getExpireAt() - nowSecs);
            info.setErrorReason(String.format("License 将在约 %d 天后过期，请及时续期", remainDays));
            return info;
        }

        // 5. 配额校验
        if (currentUsers >= 0 && claims.getMaxUsers() > 0 && currentUsers > claims.getMaxUsers()) {
            return LicenseStatusInfo.ofError(LicenseStatus.INVALID,
                    String.format("用户数超出 License 配额：当前 %d，限制 %d",
                            currentUsers, claims.getMaxUsers()));
        }
        if (currentSdk >= 0 && claims.getMaxSdkInstances() > 0 && currentSdk > claims.getMaxSdkInstances()) {
            return LicenseStatusInfo.ofError(LicenseStatus.INVALID,
                    String.format("SDK 实例数超出 License 配额：当前 %d，限制 %d",
                            currentSdk, claims.getMaxSdkInstances()));
        }

        return LicenseStatusInfo.ofClaims(LicenseStatus.VALID, claims);
    }
}
