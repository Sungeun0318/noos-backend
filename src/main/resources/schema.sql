-- NOOS backend MySQL schema

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(255) NULL,
    password_hash VARCHAR(255) NULL,
    display_name VARCHAR(100) NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'local',
    provider_id VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_provider_login_id (provider, login_id),
    UNIQUE KEY uk_users_provider_provider_id (provider, provider_id),
    KEY idx_users_login_id (login_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NULL,
    session_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'completed',
    planet VARCHAR(50) NULL,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    KEY idx_sessions_user_id (user_id),
    KEY idx_sessions_session_type (session_type),
    KEY idx_sessions_created_at (created_at),
    CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eeg_results (
    eeg_result_id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    device_type VARCHAR(100) NULL,
    measured_at DATETIME NULL,
    analyzed_at DATETIME NULL,
    sample_rate_hz DECIMAL(8,3) NULL,
    measurement_duration_sec INT NULL,
    sample_count INT NOT NULL DEFAULT 0,
    dominant_band VARCHAR(30) NULL,
    delta_percent DECIMAL(8,4) NULL,
    theta_percent DECIMAL(8,4) NULL,
    alpha_percent DECIMAL(8,4) NULL,
    beta_percent DECIMAL(8,4) NULL,
    gamma_percent DECIMAL(8,4) NULL,
    quality_usable BOOLEAN NULL,
    quality_score DECIMAL(5,3) NULL,
    state_key VARCHAR(80) NULL,
    state_label VARCHAR(150) NULL,
    request_payload_json JSON NULL,
    recognition_result_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (eeg_result_id),
    KEY idx_eeg_results_session_id (session_id),
    KEY idx_eeg_results_measured_at (measured_at),
    KEY idx_eeg_results_state_key (state_key),
    CONSTRAINT fk_eeg_results_session
        FOREIGN KEY (session_id) REFERENCES sessions (session_id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
