-- 1. Application User Creation
CREATE USER MS_USR WITH PASSWORD 'ms_usr_password';

-- Create AUTH schema
CREATE SCHEMA IF NOT EXISTS auth;

-- Grant permissions
GRANT CONNECT ON DATABASE "db-auth" TO MS_USR;
GRANT USAGE ON SCHEMA auth TO MS_USR;

-- Set search path for MS_USR
ALTER USER MS_USR SET search_path TO auth, public;

-- Grant default privileges for new tables and sequences in AUTH schema
ALTER DEFAULT PRIVILEGES IN SCHEMA auth
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO MS_USR;

ALTER DEFAULT PRIVILEGES IN SCHEMA auth
GRANT USAGE, SELECT ON SEQUENCES TO MS_USR;
