CREATE TABLE adaptive_session_feedback (
  adaptive_session_id VARCHAR(40) PRIMARY KEY,
  music_fit           DOUBLE,
  focus_relax_help    DOUBLE,
  transition_natural  DOUBLE,
  memo                VARCHAR(500),
  skipped             TINYINT(1) NOT NULL,
  created_at          DATETIME NOT NULL
);
