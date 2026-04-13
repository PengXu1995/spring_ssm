-- License Management Schema
-- Compatible with MySQL 5.7+ and H2 (for testing)

CREATE TABLE IF NOT EXISTS license_info (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  tenant_id       VARCHAR(64)  NOT NULL UNIQUE,
  license_key     TEXT         NOT NULL,
  status          VARCHAR(32)  NOT NULL DEFAULT 'NOT_CONFIGURED',
  status_message  VARCHAR(512),
  days_until_expiry BIGINT,
  last_verified_at  DATETIME,
  last_error      VARCHAR(512),
  updated_by      VARCHAR(64),
  updated_at      DATETIME,
  updater_ip      VARCHAR(64),
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS license_audit_log (
  id                  BIGINT       PRIMARY KEY AUTO_INCREMENT,
  tenant_id           VARCHAR(64)  NOT NULL,
  action              VARCHAR(64)  NOT NULL,
  operator_id         VARCHAR(64),
  operator_ip         VARCHAR(64),
  old_license_digest  VARCHAR(128),
  new_license_digest  VARCHAR(128),
  result              VARCHAR(16)  NOT NULL,
  error_message       VARCHAR(512),
  created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_created
    ON license_audit_log(tenant_id, created_at);
