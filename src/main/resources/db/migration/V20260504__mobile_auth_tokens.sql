CREATE TABLE mobile_auth_tokens (
  id              VARCHAR(40) PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  device_id       VARCHAR(64) NOT NULL,
  access_jti      VARCHAR(64) NOT NULL,
  refresh_token   VARCHAR(255) NOT NULL,
  expires_at      DATETIME NOT NULL,
  revoked_at      DATETIME NULL,
  created_at      DATETIME NOT NULL,
  last_used_at    DATETIME,
  INDEX idx_user (user_id),
  INDEX idx_device (device_id),
  UNIQUE KEY uniq_jti (access_jti)
);
