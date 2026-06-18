ALTER TABLE email_verification_tokens
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0 AFTER expires_at;
