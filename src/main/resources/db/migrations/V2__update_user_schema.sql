-- Update users table to match User entity schema
-- Add missing columns and rename existing ones

-- Rename 'name' column to 'username' and adjust constraints
ALTER TABLE users RENAME COLUMN name TO username;
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(50);
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_username_unique UNIQUE (username);

-- Add missing columns from User entity
ALTER TABLE users ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN full_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add indexes for performance
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(active);
