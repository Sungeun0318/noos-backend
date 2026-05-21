CREATE TABLE mobile_sessions (
  id              VARCHAR(40) PRIMARY KEY,
  user_id         BIGINT NULL,
  device_id       VARCHAR(64) NOT NULL,
  planet          VARCHAR(32) NOT NULL,
  duration_sec    INT NOT NULL,
  state_label     VARCHAR(64),
  current_state   JSON,
  intent_text     VARCHAR(500),
  recognition_id  VARCHAR(40) NULL,
  audio_id        VARCHAR(40) NULL,
  lighting_job_id VARCHAR(40) NULL,
  source          VARCHAR(16),
  status          VARCHAR(16) NOT NULL,
  error_code      VARCHAR(40) NULL,
  error_message   VARCHAR(500) NULL,
  created_at      DATETIME NOT NULL,
  started_at      DATETIME NULL,
  completed_at    DATETIME NULL,
  deleted_at      DATETIME NULL,
  INDEX idx_device_created (device_id, created_at),
  INDEX idx_user_created   (user_id, created_at),
  INDEX idx_status         (status)
);

CREATE TABLE generated_audio (
  id            VARCHAR(40) PRIMARY KEY,
  session_id    VARCHAR(40) NOT NULL,
  storage_type  VARCHAR(16) NOT NULL,
  storage_ref   VARCHAR(512) NOT NULL,
  mime          VARCHAR(64),
  duration_sec  INT,
  size_bytes    BIGINT,
  source_worker VARCHAR(64),
  created_at    DATETIME NOT NULL,
  expires_at    DATETIME NULL,
  INDEX idx_session (session_id)
);

CREATE TABLE state_measurements (
  id                 VARCHAR(40) PRIMARY KEY,
  user_id            BIGINT NULL,
  device_id          VARCHAR(64) NOT NULL,
  source             VARCHAR(16) NOT NULL,
  survey_json        JSON NULL,
  eeg_json           JSON NULL,
  eeg_device_type    VARCHAR(64),
  signal_quality     DOUBLE,
  state_label        VARCHAR(64),
  current_state      JSON,
  recommended_planet VARCHAR(32),
  alternates_json    JSON,
  confidence         DOUBLE,
  weight_survey      DOUBLE,
  weight_eeg         DOUBLE,
  measured_at        DATETIME NOT NULL,
  created_at         DATETIME NOT NULL,
  INDEX idx_device_measured (device_id, measured_at)
);

CREATE TABLE session_feedback (
  session_id    VARCHAR(40) PRIMARY KEY,
  music_fit     DOUBLE,
  lighting_fit  DOUBLE,
  focus_result  DOUBLE,
  memo          VARCHAR(500),
  created_at    DATETIME NOT NULL
);

CREATE TABLE lighting_jobs (
  id           VARCHAR(40) PRIMARY KEY,
  session_id   VARCHAR(40),
  planet       VARCHAR(32),
  status       VARCHAR(16),
  started_at   DATETIME,
  stopped_at   DATETIME NULL,
  INDEX idx_session (session_id)
);

CREATE TABLE idempotency_keys (
  k             VARCHAR(64) PRIMARY KEY,
  scope         VARCHAR(64) NOT NULL,
  device_id     VARCHAR(64) NOT NULL,
  response_hash VARCHAR(64),
  response_body MEDIUMTEXT,
  created_at    DATETIME NOT NULL,
  expires_at    DATETIME NOT NULL,
  INDEX idx_expires (expires_at)
);
