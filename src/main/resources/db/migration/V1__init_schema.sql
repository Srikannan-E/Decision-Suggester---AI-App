-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Use VARCHAR columns for decision status and verdict values so JPA enum/string persistence works cleanly

-- Products table stores fetched product information
CREATE TABLE products (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    url VARCHAR(2000),
    price DECIMAL(10, 2),
    rating DECIMAL(3, 2),
    review_count INTEGER,
    category VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Decision queries track each user request and its processing status
CREATE TABLE decision_queries (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    product_id UUID REFERENCES products(id),
    input_text VARCHAR(2000) NOT NULL,
    category VARCHAR(200) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Decision results hold the AI-generated verdict, pros, cons, and metadata
CREATE TABLE decision_results (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    query_id UUID REFERENCES decision_queries(id) UNIQUE,
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

-- Indexes for fast lookups
CREATE INDEX idx_query_trgm ON decision_queries USING gin (query gin_trgm_ops);
CREATE INDEX idx_decision_queries_status ON decision_queries(status);
CREATE INDEX idx_decision_queries_created_at ON decision_queries(created_at DESC);
CREATE INDEX idx_decision_results_query_id ON decision_results(query_id);
CREATE INDEX idx_decision_results_verdict ON decision_results(verdict);
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_name_trgm ON products USING gin(name gin_trgm_ops);