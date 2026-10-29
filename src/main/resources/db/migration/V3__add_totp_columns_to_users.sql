-- ===================================================
-- V3__add_totp_columns_to_users.sql
-- Add TOTP secret and enablement flag to users table
-- ===================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_totp_enabled BOOLEAN DEFAULT FALSE;
