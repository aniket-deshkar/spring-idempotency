CREATE TABLE IF NOT EXISTS idempotency_record (
  idempotency_key VARCHAR(512) PRIMARY KEY,
  fingerprint VARCHAR(64) NOT NULL,
  state VARCHAR(20) NOT NULL,
  owner_token VARCHAR(64),
  expires_at TIMESTAMPTZ NOT NULL,
  response_status INTEGER,
  response_content_type VARCHAR(255),
  response_headers TEXT,
  response_body BYTEA
);

CREATE INDEX IF NOT EXISTS idempotency_record_expires_at_idx
  ON idempotency_record (expires_at);
