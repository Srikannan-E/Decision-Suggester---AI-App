-- Convert existing enum-backed columns to VARCHAR so JPA string persistence works reliably
ALTER TABLE decision_queries ALTER COLUMN status TYPE VARCHAR(20) USING status::text;
ALTER TABLE decision_results ALTER COLUMN verdict TYPE VARCHAR(20) USING verdict::text;
