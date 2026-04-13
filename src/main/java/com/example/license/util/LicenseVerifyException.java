package com.example.license.util;

/**
 * License 验签异常。
 */
public class LicenseVerifyException extends Exception {

    public LicenseVerifyException(String message) {
        super(message);
    }

    public LicenseVerifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
