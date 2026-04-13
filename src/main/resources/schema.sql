-- License 信息表（单行）
CREATE TABLE IF NOT EXISTS license_info (
    id              INT             NOT NULL PRIMARY KEY DEFAULT 1,
    license_key     CLOB,
    last_validated_at BIGINT        DEFAULT 0,
    last_seen_timestamp BIGINT      DEFAULT 0,
    updated_at      BIGINT          DEFAULT 0
);

-- License 审计日志表
CREATE TABLE IF NOT EXISTS license_audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator        VARCHAR(100),
    operator_ip     VARCHAR(50),
    old_license_id  VARCHAR(200),
    new_license_id  VARCHAR(200),
    action          VARCHAR(50),
    result          VARCHAR(20),
    error_msg       VARCHAR(1000),
    created_at      BIGINT          DEFAULT 0
);
