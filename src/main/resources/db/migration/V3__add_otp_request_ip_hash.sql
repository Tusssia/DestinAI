ALTER TABLE otp_tokens
    ADD COLUMN request_ip_hash VARCHAR(255);

CREATE INDEX idx_otp_tokens_ip_created ON otp_tokens (request_ip_hash, created_at DESC);

