package com.example.license.util;

import com.example.license.model.LicenseClaims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * LicenseCrypto 单元测试：覆盖验签、格式校验、脱敏等场景。
 */
class LicenseCryptoTest {

    private static byte[] privateKeyBytes;
    private static byte[] publicKeyBytes;
    private static byte[] anotherPublicKeyBytes;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);

        KeyPair kp = gen.generateKeyPair();
        privateKeyBytes = kp.getPrivate().getEncoded();
        publicKeyBytes  = kp.getPublic().getEncoded();

        // 另一对密钥，用于模拟密钥不匹配
        KeyPair other = gen.generateKeyPair();
        anotherPublicKeyBytes = other.getPublic().getEncoded();
    }

    private LicenseClaims buildClaims() {
        LicenseClaims c = new LicenseClaims();
        c.setLicenseId("lic-test-001");
        c.setTenantId("tenant-abc");
        c.setCustomerName("测试客户");
        c.setPlan("enterprise");
        c.setIssueAt(1_700_000_000L);
        c.setExpireAt(2_000_000_000L);
        c.setGracePeriodDays(7);
        c.setIssuer("DataMask");
        c.setMaxUsers(100);
        c.setMaxSdkInstances(10);
        c.setMaxDailyRecords(500_000L);
        c.setFeatures(List.of("video_desensitize", "api_access", "audit"));
        return c;
    }

    @Test
    void signAndVerify_shouldSucceed() throws Exception {
        LicenseClaims claims = buildClaims();
        String licenseKey = LicenseCrypto.sign(claims, privateKeyBytes);

        assertThat(licenseKey).contains(".");

        LicenseClaims parsed = LicenseCrypto.verify(licenseKey, publicKeyBytes);
        assertThat(parsed.getLicenseId()).isEqualTo("lic-test-001");
        assertThat(parsed.getTenantId()).isEqualTo("tenant-abc");
        assertThat(parsed.getCustomerName()).isEqualTo("测试客户");
        assertThat(parsed.getPlan()).isEqualTo("enterprise");
        assertThat(parsed.getMaxUsers()).isEqualTo(100);
        assertThat(parsed.getFeatures()).containsExactlyInAnyOrder("video_desensitize", "api_access", "audit");
    }

    @Test
    void verify_withWrongPublicKey_shouldThrow() throws Exception {
        LicenseClaims claims = buildClaims();
        String licenseKey = LicenseCrypto.sign(claims, privateKeyBytes);

        assertThatThrownBy(() -> LicenseCrypto.verify(licenseKey, anotherPublicKeyBytes))
                .isInstanceOf(LicenseVerifyException.class)
                .hasMessageContaining("签名验证失败");
    }

    @Test
    void verify_withTamperedClaims_shouldThrow() throws Exception {
        LicenseClaims claims = buildClaims();
        String licenseKey = LicenseCrypto.sign(claims, privateKeyBytes);

        // 篡改：替换 claims 部分
        String[] parts = licenseKey.split("\\.");
        String originalClaimsB64 = parts[0];
        // 修改最后一个字符
        char lastChar = originalClaimsB64.charAt(originalClaimsB64.length() - 1);
        char modChar = lastChar == 'A' ? 'B' : 'A';
        String tamperedClaims = originalClaimsB64.substring(0, originalClaimsB64.length() - 1) + modChar;
        String tampered = tamperedClaims + "." + parts[1];

        assertThatThrownBy(() -> LicenseCrypto.verify(tampered, publicKeyBytes))
                .isInstanceOf(LicenseVerifyException.class);
    }

    @Test
    void verify_withTamperedSignature_shouldThrow() throws Exception {
        LicenseClaims claims = buildClaims();
        String licenseKey = LicenseCrypto.sign(claims, privateKeyBytes);

        String[] parts = licenseKey.split("\\.", 2);
        // 替换签名为全 A
        String badSig = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String tampered = parts[0] + "." + badSig;

        assertThatThrownBy(() -> LicenseCrypto.verify(tampered, publicKeyBytes))
                .isInstanceOf(LicenseVerifyException.class);
    }

    @Test
    void verify_withNullKey_shouldThrow() {
        assertThatThrownBy(() -> LicenseCrypto.verify(null, publicKeyBytes))
                .isInstanceOf(LicenseVerifyException.class)
                .hasMessageContaining("为空");
    }

    @Test
    void verify_withMissingDot_shouldThrow() {
        assertThatThrownBy(() -> LicenseCrypto.verify("nodotinhere", publicKeyBytes))
                .isInstanceOf(LicenseVerifyException.class)
                .hasMessageContaining("格式错误");
    }

    @Test
    void maskLicenseKey_shouldObfuscateMiddle() {
        String key = "abcd1234567890wxyz";
        String masked = LicenseCrypto.maskLicenseKey(key);
        assertThat(masked).startsWith("abcd");
        assertThat(masked).endsWith("wxyz");
        assertThat(masked).contains("****");
        assertThat(masked).doesNotContain("12345");
    }

    @Test
    void maskLicenseKey_withShortKey_shouldReturnAsterisks() {
        assertThat(LicenseCrypto.maskLicenseKey("short")).isEqualTo("****");
    }
}
