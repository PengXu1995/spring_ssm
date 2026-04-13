package com.example.license;

import com.example.license.model.LicenseClaims;
import com.example.license.model.LicenseInfo;
import com.example.license.model.LicenseStatus;
import com.example.license.service.LicenseService;
import com.example.license.service.LicenseSigningService;
import com.example.license.service.LicenseSigningService.LicenseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Spy
    private LicenseSigningService signingService;

    @InjectMocks
    private LicenseService licenseService;

    private Map<String, String> keyPair;

    @BeforeEach
    void setUp() throws LicenseException {
        keyPair = signingService.generateKeyPair();
    }

    // 1. Sign and verify a valid license
    @Test
    void testSignAndVerifyLicense() throws LicenseException {
        LicenseClaims claims = buildClaims("T001", false, nowPlusDays(90), 7);
        String token = signingService.signLicense(claims, keyPair.get("privateKey"));
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);

        LicenseClaims verified = signingService.verifyLicense(token, keyPair.get("publicKey"));
        assertEquals("T001", verified.getTenantId());
        assertEquals("SUBSCRIPTION", verified.getLicenseType());
        assertFalse(verified.isRevoked());
    }

    // 2. Expired license
    @Test
    void testExpiredLicense() throws LicenseException {
        // Expired 2 days ago, grace period = 0
        LicenseClaims claims = buildClaims("T002", false, nowMinusDays(2), 0);
        String token = signingService.signLicense(claims, keyPair.get("privateKey"));
        LicenseClaims verified = signingService.verifyLicense(token, keyPair.get("publicKey"));

        LicenseStatus status = licenseService.computeStatus(verified);
        assertEquals(LicenseStatus.EXPIRED, status);
    }

    // 3. Grace period license
    @Test
    void testGracePeriodLicense() throws LicenseException {
        // Expired 3 days ago, grace period = 7 days
        LicenseClaims claims = buildClaims("T003", false, nowMinusDays(3), 7);
        String token = signingService.signLicense(claims, keyPair.get("privateKey"));
        LicenseClaims verified = signingService.verifyLicense(token, keyPair.get("publicKey"));

        LicenseStatus status = licenseService.computeStatus(verified);
        assertEquals(LicenseStatus.IN_GRACE_PERIOD, status);
    }

    // 4. Revoked license
    @Test
    void testRevokedLicense() throws LicenseException {
        LicenseClaims claims = buildClaims("T004", true, nowPlusDays(90), 7);
        claims.setRevokedReason("Policy violation");
        String token = signingService.signLicense(claims, keyPair.get("privateKey"));
        LicenseClaims verified = signingService.verifyLicense(token, keyPair.get("publicKey"));

        LicenseStatus status = licenseService.computeStatus(verified);
        assertEquals(LicenseStatus.REVOKED, status);
    }

    // 5. Capacity limit — maxUsers check
    @Test
    void testCapacityLimit() throws LicenseException {
        LicenseClaims claims = buildClaims("T005", false, nowPlusDays(90), 7);
        claims.setMaxUsers(5);
        claims.setHardLimit(true);

        String token = signingService.signLicense(claims, keyPair.get("privateKey"));
        LicenseClaims verified = signingService.verifyLicense(token, keyPair.get("publicKey"));

        assertEquals(5, verified.getMaxUsers());
        assertTrue(verified.isHardLimit());
        // Simulate enforcement: current user count (6) > maxUsers (5)
        int currentUsers = 6;
        assertTrue(verified.isHardLimit() && currentUsers > verified.getMaxUsers(),
                "Should enforce hard limit when over capacity");
    }

    // 6. Feature flag check
    @Test
    void testFeatureEnabled() throws LicenseException {
        LicenseClaims claims = buildClaims("T006", false, nowPlusDays(90), 7);
        claims.setFeatures(Arrays.asList("VIDEO_DESENSITIZE", "API", "AUDIT"));
        String token = signingService.signLicense(claims, keyPair.get("privateKey"));
        LicenseClaims verified = signingService.verifyLicense(token, keyPair.get("publicKey"));

        assertTrue(verified.getFeatures().contains("VIDEO_DESENSITIZE"), "VIDEO_DESENSITIZE should be enabled");
        assertTrue(verified.getFeatures().contains("API"), "API should be enabled");
        assertFalse(verified.getFeatures().contains("SDK"), "SDK should NOT be enabled");
        assertFalse(verified.getFeatures().contains("EXPORT"), "EXPORT should NOT be enabled");
    }

    // 7. Clock tamper detection
    @Test
    void testClockTampering() throws LicenseException {
        LicenseClaims claims = buildClaims("T007", false, nowPlusDays(90), 7);
        // Set issuedAt 10 minutes in the future (> 5 min tolerance)
        claims.setIssuedAt(Instant.now().getEpochSecond() + 600L);
        String token = signingService.signLicense(claims, keyPair.get("privateKey"));
        LicenseClaims verified = signingService.verifyLicense(token, keyPair.get("publicKey"));

        LicenseStatus status = licenseService.computeStatus(verified);
        assertEquals(LicenseStatus.CLOCK_TAMPERED, status);
    }

    // 8. Invalid signature / tampered token
    @Test
    void testInvalidSignature() throws LicenseException {
        LicenseClaims claims = buildClaims("T008", false, nowPlusDays(90), 7);
        String token = signingService.signLicense(claims, keyPair.get("privateKey"));

        // Tamper: replace last character of signature part
        String[] parts = token.split("\\.");
        String tamperedSig = parts[2].substring(0, parts[2].length() - 1) + "X";
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSig;

        LicenseException ex = assertThrows(LicenseException.class,
                () -> signingService.verifyLicense(tamperedToken, keyPair.get("publicKey")));
        assertTrue(ex.getMessage().toLowerCase().contains("signature") ||
                   ex.getMessage().toLowerCase().contains("verification") ||
                   ex.getMessage().toLowerCase().contains("tamper"),
                "Exception message should indicate signature problem, got: " + ex.getMessage());
    }

    // 9. maskLicenseKey masks middle of key
    @Test
    void testMaskLicenseKey() {
        LicenseInfo info = new LicenseInfo();
        info.setLicenseKey("ABCDEFGH.payload.SIGNATURE1234");
        licenseService.maskLicenseKey(info);
        String masked = info.getLicenseKey();
        assertTrue(masked.startsWith("ABCDEFGH"), "Should keep first 8 chars");
        assertTrue(masked.contains("****"), "Should contain mask");
        assertTrue(masked.endsWith("1234"), "Should keep last 4 chars");
    }

    // 10. computeStatus returns VALID for long-lived license
    @Test
    void testValidStatus() throws LicenseException {
        LicenseClaims claims = buildClaims("T010", false, nowPlusDays(365), 7);
        LicenseStatus status = licenseService.computeStatus(claims);
        assertEquals(LicenseStatus.VALID, status);
    }

    // ---- helpers ----

    private LicenseClaims buildClaims(String tenantId, boolean revoked, long expiresAt, long graceDays) {
        LicenseClaims c = new LicenseClaims();
        c.setLicenseId(java.util.UUID.randomUUID().toString());
        c.setTenantId(tenantId);
        c.setTenantName("Test Tenant " + tenantId);
        c.setLicenseType("SUBSCRIPTION");
        c.setIssuedAt(Instant.now().getEpochSecond() - 60);
        c.setExpiresAt(expiresAt);
        c.setGracePeriodDays(graceDays);
        c.setMaxUsers(100);
        c.setMaxConcurrent(20);
        c.setMaxTasks(10000);
        c.setMaxDataSources(50);
        c.setMaxSdkInstances(10);
        c.setFeatures(Arrays.asList("VIDEO_DESENSITIZE", "API"));
        c.setEnvironment("PRODUCTION");
        c.setHardLimit(false);
        c.setRevoked(revoked);
        c.setIssuer("com.example.license");
        return c;
    }

    private long nowPlusDays(long days) {
        return Instant.now().getEpochSecond() + days * 86400L;
    }

    private long nowMinusDays(long days) {
        return Instant.now().getEpochSecond() - days * 86400L;
    }
}
