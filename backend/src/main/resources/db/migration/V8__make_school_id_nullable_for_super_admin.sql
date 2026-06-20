-- Make school_id nullable in auth.users and auth.user_school_roles to support SUPER_ADMIN
ALTER TABLE auth.users ALTER COLUMN school_id DROP NOT NULL;
ALTER TABLE auth.user_school_roles ALTER COLUMN school_id DROP NOT NULL;
