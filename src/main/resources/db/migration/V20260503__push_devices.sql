CREATE TABLE push_devices (
  id              VARCHAR(40) PRIMARY KEY,
  device_id       VARCHAR(64) NOT NULL,
  user_id         BIGINT NULL,
  platform        VARCHAR(16) NOT NULL,
  provider        VARCHAR(16) NOT NULL,
  token           VARCHAR(512) NOT NULL,
  app_version     VARCHAR(32),
  locale          VARCHAR(16),
  active          TINYINT(1) DEFAULT 1,
  last_seen_at    DATETIME,
  created_at      DATETIME NOT NULL,
  UNIQUE KEY uniq_device_provider (device_id, provider),
  INDEX idx_user (user_id),
  INDEX idx_active (active, last_seen_at)
);
