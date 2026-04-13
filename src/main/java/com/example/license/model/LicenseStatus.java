package com.example.license.model;

public enum LicenseStatus {
    VALID,
    EXPIRING_SOON,
    EXPIRED,
    IN_GRACE_PERIOD,
    REVOKED,
    CLOCK_TAMPERED,
    INVALID_SIGNATURE,
    NOT_CONFIGURED
}
