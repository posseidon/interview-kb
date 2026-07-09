-- FK column on answer(question_id) has no index — every answers JOIN does a seq scan
CREATE INDEX IF NOT EXISTS idx_answer_question_id ON answer(question_id);

-- Composite index for the ORDER BY used on all question list queries
CREATE INDEX IF NOT EXISTS idx_question_freq_created ON question(frequency DESC, created_at DESC);
