package com.example.license.service;

import com.example.license.model.LicenseClaims;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * RSA-2048 based license signing and verification service.
 * Token format: base64url(header).base64url(claimsJson).base64url(signature)
 *
 * NOTE: DEMO_PUBLIC_KEY / DEMO_PRIVATE_KEY are generated at class-load time
 * for development and testing ONLY. In production, supply keys via configuration.
 */
@Service
public class LicenseSigningService {

    private static final Logger log = LoggerFactory.getLogger(LicenseSigningService.class);
    private static final String ALGORITHM = "SHA256withRSA";
    private static final String HEADER_JSON = "{\"alg\":\"RS256\",\"typ\":\"LICENSE\"}";

    /**
     * Demo RSA-2048 key pair generated at class load time.
     * FOR DEVELOPMENT/TESTING ONLY — keys change on every JVM restart.
     * Supply {@code license.public.key} in db.properties for production use.
     */
    public static final String DEMO_PUBLIC_KEY;
    public static final String DEMO_PRIVATE_KEY;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            DEMO_PUBLIC_KEY = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            DEMO_PRIVATE_KEY = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates a new RSA-2048 key pair and returns them as Base64-encoded strings.
     */
    public Map<String, String> generateKeyPair() throws LicenseException {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            Map<String, String> result = new HashMap<>();
            result.put("publicKey", Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
            result.put("privateKey", Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new LicenseException("Failed to generate key pair: " + e.getMessage(), e);
        }
    }

    /**
     * Signs a LicenseClaims and returns a license token string.
     */
    public String signLicense(LicenseClaims claims, String privateKeyBase64) throws LicenseException {
        try {
            String headerB64 = base64UrlEncode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String claimsJson = objectMapper.writeValueAsString(claims);
            String claimsB64 = base64UrlEncode(claimsJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = headerB64 + "." + claimsB64;

            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(keySpec);

            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signature = sig.sign();

            return signingInput + "." + base64UrlEncode(signature);
        } catch (Exception e) {
            throw new LicenseException("Failed to sign license: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies a license token and returns the parsed LicenseClaims.
     */
    public LicenseClaims verifyLicense(String licenseToken, String publicKeyBase64) throws LicenseException {
        if (licenseToken == null || licenseToken.isBlank()) {
            throw new LicenseException("License token is null or empty");
        }
        String[] parts = licenseToken.trim().split("\\.");
        if (parts.length != 3) {
            throw new LicenseException("Invalid license token format: expected 3 parts, got " + parts.length);
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] signatureBytes = base64UrlDecode(parts[2]);

            byte[] pubKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKeyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(keySpec);

            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            boolean valid = sig.verify(signatureBytes);

            if (!valid) {
                throw new LicenseException("Invalid signature — license token has been tampered with");
            }

            byte[] claimsBytes = base64UrlDecode(parts[1]);
            String claimsJson = new String(claimsBytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(claimsJson, LicenseClaims.class);
        } catch (LicenseException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseException("License verification failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the SHA-256 hex digest of the given token (for audit logging).
     */
    public String getTokenDigest(String token) throws LicenseException {
        if (token == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new LicenseException("SHA-256 not available", e);
        }
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    /**
     * Checked exception for all license-related errors.
     */
    public static class LicenseException extends Exception {
        public LicenseException(String message) { super(message); }
        public LicenseException(String message, Throwable cause) { super(message, cause); }
    }
}
