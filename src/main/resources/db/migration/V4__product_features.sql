ALTER TABLE products
    ADD COLUMN IF NOT EXISTS feature_highlights TEXT[];

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS spec_summary TEXT;
