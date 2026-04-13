package com.example.license.util;

import com.example.license.model.LicenseClaims;
import com.example.license.model.LicenseStatus;
import com.example.license.model.LicenseStatusInfo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * LicenseValidator 单元测试：覆盖有效、即将过期、宽限期、已过期、时间回拨、配额超限等场景。
 */
class LicenseValidatorTest {

    private static final long NOW = 1_750_000_000L;  // 固定基准时间（秒）

    private LicenseClaims buildClaims(long expireAt, int graceDays) {
        LicenseClaims c = new LicenseClaims();
        c.setLicenseId("lic-001");
        c.setTenantId("t1");
        c.setCustomerName("Test");
        c.setPlan("enterprise");
        c.setIssueAt(NOW - TimeUnit.DAYS.toSeconds(30));
        c.setExpireAt(expireAt);
        c.setGracePeriodDays(graceDays);
        c.setIssuer("DataMask");
        c.setMaxUsers(50);
        c.setMaxSdkInstances(5);
        c.setMaxDailyRecords(-1);
        return c;
    }

    @Test
    void validate_withinExpiry_shouldBeValid() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.VALID);
    }

    @Test
    void validate_expiringSoon_lessThan7Days_shouldBeExpiringSoon() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(3);  // 3天后到期
        LicenseClaims c = buildClaims(expireAt, 7);
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.EXPIRING_SOON);
        assertThat(result.getErrorReason()).contains("过期");
    }

    @Test
    void validate_withinGracePeriod_shouldBeExpiringSoon() {
        long expireAt = NOW - TimeUnit.DAYS.toSeconds(2);  // 2天前到期，宽限期7天
        LicenseClaims c = buildClaims(expireAt, 7);
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.EXPIRING_SOON);
        assertThat(result.getErrorReason()).contains("宽限期");
    }

    @Test
    void validate_beyondGracePeriod_shouldBeExpired() {
        long expireAt = NOW - TimeUnit.DAYS.toSeconds(10);  // 10天前到期，宽限期7天
        LicenseClaims c = buildClaims(expireAt, 7);
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.EXPIRED);
        assertThat(result.getErrorReason()).contains("过期");
    }

    @Test
    void validate_exactlyAtExpiry_shouldBeExpiringSoon() {
        long expireAt = NOW;  // 刚好到期，但宽限期内
        LicenseClaims c = buildClaims(expireAt, 7);
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.EXPIRING_SOON);
    }

    @Test
    void validate_zeroGracePeriod_beyondExpiry_shouldBeExpired() {
        long expireAt = NOW - 1;  // 1秒前到期，无宽限期
        LicenseClaims c = buildClaims(expireAt, 0);
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.EXPIRED);
    }

    @Test
    void validate_timeRollback_shouldBeTimeTampered() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        // 当前时间比 lastSeen 早了10分钟（超过容忍60秒）
        long nowRolledBack = NOW - TimeUnit.MINUTES.toSeconds(10);
        long lastSeen = NOW;
        LicenseStatusInfo result = LicenseValidator.validate(c, nowRolledBack, lastSeen, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.TIME_TAMPERED);
        assertThat(result.getErrorReason()).contains("时间回拨");
    }

    @Test
    void validate_timeRollbackWithinTolerance_shouldBeValid() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        // 回拨30秒，在容忍范围内
        long nowSlightlyBack = NOW - 30;
        long lastSeen = NOW;
        LicenseStatusInfo result = LicenseValidator.validate(c, nowSlightlyBack, lastSeen, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.VALID);
    }

    @Test
    void validate_withZeroLastSeen_shouldNotCheckRollback() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        // lastSeen=0 表示首次，不检测时间回拨
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.VALID);
    }

    @Test
    void validate_usersOverQuota_shouldBeInvalid() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        // c.maxUsers = 50, 当前 60
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, 60, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.INVALID);
        assertThat(result.getErrorReason()).contains("用户数超出");
    }

    @Test
    void validate_sdkOverQuota_shouldBeInvalid() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        // c.maxSdkInstances = 5, 当前 8
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, -1, 8);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.INVALID);
        assertThat(result.getErrorReason()).contains("SDK 实例数超出");
    }

    @Test
    void validate_usersWithinQuota_shouldBeValid() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        // c.maxUsers = 50, 当前 50（恰好不超）
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, 50, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.VALID);
    }

    @Test
    void validate_unlimitedUsers_currentUsersIgnored() {
        long expireAt = NOW + TimeUnit.DAYS.toSeconds(30);
        LicenseClaims c = buildClaims(expireAt, 7);
        c.setMaxUsers(-1);  // 不限
        LicenseStatusInfo result = LicenseValidator.validate(c, NOW, 0, 9999, -1);
        assertThat(result.getStatus()).isEqualTo(LicenseStatus.VALID);
    }
}
