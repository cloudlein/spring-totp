-- ===================================================
-- V4__create_user_device_table.sql
-- Create user_device table for device tracking and MFA
-- ===================================================

CREATE TABLE IF NOT EXISTS user_device (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_details VARCHAR(255),
    device_hash VARCHAR(64) NOT NULL,
    ip_address VARCHAR(45),
    location VARCHAR(255),
    finger_print VARCHAR(255),
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_device_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Indexing for lookup and constraints
CREATE INDEX IF NOT EXISTS idx_user_device_hash ON user_device (device_hash);
CREATE INDEX IF NOT EXISTS idx_user_device_user_hash ON user_device (user_id, device_hash);
CREATE INDEX IF NOT EXISTS idx_user_device_user_id ON user_device (user_id);

