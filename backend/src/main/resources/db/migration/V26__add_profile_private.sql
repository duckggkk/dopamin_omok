ALTER TABLE users
    ADD COLUMN profile_private BOOLEAN NOT NULL DEFAULT FALSE AFTER email_verified;
