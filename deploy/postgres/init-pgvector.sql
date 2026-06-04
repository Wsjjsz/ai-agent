DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'supabase_admin') THEN
        CREATE ROLE supabase_admin NOLOGIN SUPERUSER;
    END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS vector;
