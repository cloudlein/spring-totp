-- ===================================================
-- V2__seed_roles_and_admin_user.sql
-- Seeding data awal: role ADMIN, USER dan 1 akun admin default
-- ===================================================

-- 1. Insert role ADMIN dan USER jika belum ada
INSERT INTO roles (role, created_at, updated_at)
VALUES 
    ('ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (role) DO NOTHING;

-- 2. Insert user Administrator default lengkap (Password123!)
-- Password di-hash menggunakan BCrypt (cost factor 12)
INSERT INTO users (username, name, email, password, role_id, created_at, updated_at)
VALUES (
    'superadmin',
    'Super Administrator',
    'superadmin@example.com',
    '$2a$12$YAL0X7NQPM8l2YnEWQ2sG.4iNgltQQdKPeDvzB.bZ2vFo3WT7w.S6',
    (SELECT id FROM roles WHERE role = 'ADMIN' LIMIT 1),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO NOTHING;
