-- btree_gist lets a GiST exclusion constraint combine equality on scalar columns (e.g. employee_id)
-- with range overlap, as leave_request does to forbid overlapping leave. Extensions exist once per
-- database, so it is installed here in public; tenant SQL refers to its operator classes qualified
-- (public.gist_uuid_ops) because a tenant's search_path holds only its own schema.
-- Trusted extension (PostgreSQL 13+): needs CREATE privilege on the database, not superuser.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
