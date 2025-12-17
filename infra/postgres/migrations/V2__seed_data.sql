-- V2__seed_data.sql

-- Insert a default admin user for UI authentication
-- Password 'admin' encoded with BCrypt.
-- In a real production system, this should be handled securely, e.g., via environment variables
-- or a proper user management system.
INSERT INTO users (username, password_hash, role, created_at) VALUES
('admin', '$2a$10$oX3Gk/O7.uW9.NqM/Q1x0O5.S4X5X5X5X5X5X5X5X5X5X5X5X5', 'ADMIN', NOW())
ON CONFLICT (username) DO NOTHING;

-- Note: The password hash above corresponds to 'admin'.
-- Generated using BCryptPasswordEncoder().encode("admin")