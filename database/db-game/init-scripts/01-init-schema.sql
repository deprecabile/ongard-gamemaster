-- 1. Creazione Utente Applicativo
CREATE USER MS_USR WITH PASSWORD 'ms_usr_password';

-- Create GAME schema
CREATE SCHEMA IF NOT EXISTS game;

-- Grant permissions
GRANT CONNECT ON DATABASE "db-game" TO MS_USR;
GRANT USAGE ON SCHEMA game TO MS_USR;

-- Set search path for MS_USR
ALTER USER MS_USR SET search_path TO game, public;

-- Grant default privileges for new tables and sequences in GAME schema
ALTER DEFAULT PRIVILEGES IN SCHEMA game 
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO MS_USR;

ALTER DEFAULT PRIVILEGES IN SCHEMA game 
GRANT USAGE, SELECT ON SEQUENCES TO MS_USR;
