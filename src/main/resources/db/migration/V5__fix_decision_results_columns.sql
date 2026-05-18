-- Expand decision_results table columns to handle larger text
ALTER TABLE decision_results
    ALTER COLUMN pros SET DATA TYPE TEXT,
    ALTER COLUMN cons SET DATA TYPE TEXT,
    ALTER COLUMN summary SET DATA TYPE TEXT,
    ALTER COLUMN reasoning SET DATA TYPE TEXT;

-- Add indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_decision_results_query_id 
ON decision_results(query_id);

CREATE INDEX IF NOT EXISTS idx_decision_results_created_at 
ON decision_results(created_at DESC);