-- 1. Creazione Utente Applicativo
CREATE USER MS_USR WITH PASSWORD 'ms_usr_password';

-- Create ACCOUNT schema
CREATE SCHEMA IF NOT EXISTS account;

-- Grant permissions
GRANT CONNECT ON DATABASE "db-account" TO MS_USR;
GRANT USAGE ON SCHEMA account TO MS_USR;

-- Set search path for MS_USR
ALTER USER MS_USR SET search_path TO account, public;

-- Grant default privileges for new tables and sequences in ACCOUNT schema
ALTER DEFAULT PRIVILEGES IN SCHEMA account
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO MS_USR;

ALTER DEFAULT PRIVILEGES IN SCHEMA account
    GRANT USAGE, SELECT ON SEQUENCES TO MS_USR;
