ALTER TABLE skill DROP COLUMN search_vector;

CREATE INDEX idx_skill_name_trgm ON skill USING gin (name gin_trgm_ops);
