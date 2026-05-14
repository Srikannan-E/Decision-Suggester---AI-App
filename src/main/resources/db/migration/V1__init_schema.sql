-- =========================
-- EXTENSIONS (SAFE)
-- =========================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =========================
-- PRODUCTS TABLE
-- =========================
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(500) NOT NULL,
    url VARCHAR(2000),
    price DECIMAL(10, 2),
    rating DECIMAL(3, 2),
    review_count INTEGER,
    category VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================
-- DECISION QUERIES TABLE
-- =========================
CREATE TABLE IF NOT EXISTS decision_queries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID REFERENCES products(id),
    query TEXT,
    input_text VARCHAR(2000) NOT NULL,

    -- SAFE COLUMN ADDITION STYLE
    category VARCHAR(200),
    status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- If table already existed earlier without column, ensure safety
ALTER TABLE decision_queries
ADD COLUMN IF NOT EXISTS category VARCHAR(200);

ALTER TABLE decision_queries
ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE decision_queries
ADD COLUMN IF NOT EXISTS error_message TEXT;

-- =========================
-- DECISION RESULTS TABLE
-- =========================
CREATE TABLE IF NOT EXISTS decision_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    query_id UUID UNIQUE REFERENCES decision_queries(id),
    product_id UUID REFERENCES products(id),
    verdict VARCHAR(20) NOT NULL,
    confidence_score DECIMAL(3, 2),
    pros TEXT[] NOT NULL,
    cons TEXT[] NOT NULL,
    summary TEXT NOT NULL,
    reasoning TEXT,
    llm_model VARCHAR(100),
    token_usage INTEGER,
    processing_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================
-- INDEXES (SAFE)
-- =========================

CREATE INDEX IF NOT EXISTS idx_query_trgm
ON decision_queries USING gin (query gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_decision_queries_status
ON decision_queries(status);

CREATE INDEX IF NOT EXISTS idx_decision_queries_created_at
ON decision_queries(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_decision_results_query_id
ON decision_results(query_id);

CREATE INDEX IF NOT EXISTS idx_decision_results_verdict
ON decision_results(verdict);

CREATE INDEX IF NOT EXISTS idx_products_name
ON products(name);

CREATE INDEX IF NOT EXISTS idx_products_name_trgm
ON products USING gin (name gin_trgm_ops);