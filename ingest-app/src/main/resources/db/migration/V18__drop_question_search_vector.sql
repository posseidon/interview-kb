-- question.search_vector (added in V15) was never wired into any query — the actual question
-- search path (QuestionRepository.findFilteredBySkill) uses a plain LIKE '%keyword%', not
-- full-text search. Being GENERATED ALWAYS ... STORED with a GIN index, it costs a recompute +
-- index write on every question insert/update for zero read benefit. Drop it.
DROP INDEX idx_question_search_vector;
ALTER TABLE question DROP COLUMN search_vector;
