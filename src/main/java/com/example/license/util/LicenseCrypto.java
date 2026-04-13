package com.example.license.util;

import com.example.license.model.LicenseClaims;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * License 非对称签名工具类。
 * <p>
 * License Key 格式：{@code base64url(claimsJson)}.{@code base64url(rsaSha256Signature)}
 * <ul>
 *   <li>签名覆盖 base64url(claimsJson) 的字节。</li>
 *   <li>应用侧仅持有公钥，签发侧持有私钥（离线签发）。</li>
 * </ul>
 */
public class LicenseCrypto {

    private static final String ALGORITHM = "SHA256withRSA";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LicenseCrypto() {}

    /**
     * 使用私钥对 claims 签名，生成 License Key 字符串。
     *
     * @param claims     License 声明
     * @param privateKey RSA 私钥（PKCS#8 DER 字节）
     * @return License Key 字符串
     */
    public static String sign(LicenseClaims claims, byte[] privateKey) throws Exception {
        String claimsJson = MAPPER.writeValueAsString(claims);
        byte[] claimsBytes = claimsJson.getBytes(StandardCharsets.UTF_8);
        String claimsPart = base64UrlEncode(claimsBytes);

        PrivateKey pk = loadPrivateKey(privateKey);
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(pk);
        signer.update(claimsPart.getBytes(StandardCharsets.UTF_8));
        byte[] sigBytes = signer.sign();

        return claimsPart + "." + base64UrlEncode(sigBytes);
    }

    /**
     * 使用公钥验签并解析 License Key，返回 claims。
     *
     * @param licenseKey License Key 字符串
     * @param publicKey  RSA 公钥（X.509 DER 字节）
     * @return 已验证的 LicenseClaims
     * @throws LicenseVerifyException 签名无效或格式错误时抛出
     */
    public static LicenseClaims verify(String licenseKey, byte[] publicKey) throws LicenseVerifyException {
        if (licenseKey == null || licenseKey.isBlank()) {
            throw new LicenseVerifyException("License Key 为空");
        }
        int dotIdx = licenseKey.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx >= licenseKey.length() - 1) {
            throw new LicenseVerifyException("License Key 格式错误：缺少签名分隔符");
        }
        String claimsPart = licenseKey.substring(0, dotIdx);
        String sigPart = licenseKey.substring(dotIdx + 1);

        try {
            byte[] claimsBytes = base64UrlDecode(claimsPart);
            byte[] sigBytes = base64UrlDecode(sigPart);

            PublicKey pk = loadPublicKey(publicKey);
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(pk);
            verifier.update(claimsPart.getBytes(StandardCharsets.UTF_8));
            boolean valid = verifier.verify(sigBytes);
            if (!valid) {
                throw new LicenseVerifyException("签名验证失败：License 可能已被篡改");
            }

            String claimsJson = new String(claimsBytes, StandardCharsets.UTF_8);
            return MAPPER.readValue(claimsJson, LicenseClaims.class);
        } catch (LicenseVerifyException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseVerifyException("License 解析失败：" + e.getMessage(), e);
        }
    }

    // ---- helpers ----

    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] base64UrlDecode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }

    /** 从 PEM 字符串加载公钥字节（X.509） */
    public static byte[] pemToBytes(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN.*?-----", "")
                .replaceAll("-----END.*?-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }

    private static PublicKey loadPublicKey(byte[] derBytes) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(derBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static PrivateKey loadPrivateKey(byte[] derBytes) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(derBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    /** 对 License Key 进行脱敏，保留首尾各 4 个字符 */
    public static String maskLicenseKey(String licenseKey) {
        if (licenseKey == null || licenseKey.length() <= 12) {
            return "****";
        }
        return licenseKey.substring(0, 4) + "****..." + licenseKey.substring(licenseKey.length() - 4);
    }
}
