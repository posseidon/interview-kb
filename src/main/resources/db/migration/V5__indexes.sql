-- Reverse lookup on interview_question: needed for cascade-delete and question detail joins
CREATE INDEX IF NOT EXISTS idx_interview_question_qid ON interview_question(question_id);

-- Content-hash lookup used on every ingest upsert check
CREATE INDEX IF NOT EXISTS idx_question_content_hash ON question(content_hash);
