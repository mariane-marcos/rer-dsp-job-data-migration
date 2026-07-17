-- Executar como superusuário (ex.: postgres).
-- Ex.: psql -h localhost -p 5432 -U postgres -f 01_create_database.sql
--
-- Se o banco já existir, ignore o erro de CREATE DATABASE e siga para o passo 02.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'target_user') THEN
        CREATE ROLE target_user LOGIN PASSWORD 'target_user';
    END IF;
END
$$;

CREATE DATABASE target_db OWNER target_user;

GRANT ALL PRIVILEGES ON DATABASE target_db TO target_user;
