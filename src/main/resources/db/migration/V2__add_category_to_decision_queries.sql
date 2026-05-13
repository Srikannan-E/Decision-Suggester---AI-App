-- Add category column to decision_queries
ALTER TABLE decision_queries ADD COLUMN category VARCHAR(200) NOT NULL DEFAULT '';