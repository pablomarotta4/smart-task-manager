-- Agregar columna role a la tabla users
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Crear indice para busquedas por role
CREATE INDEX idx_users_role ON users(role);
