-- Phase 1 of the topic -> skill migration: an audit table recording which skill
-- each existing topic was matched to, and the resulting question<->skill join table.
-- topic/question_topic are left untouched; they're superseded in application code
-- in a later phase, once question_skill is backfilled.
CREATE TABLE topic_skill_map (
    topic_id   UUID PRIMARY KEY REFERENCES topic(id),
    skill_id   UUID NOT NULL REFERENCES skill(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE question_skill (
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    skill_id    UUID NOT NULL REFERENCES skill(id)    ON DELETE CASCADE,
    PRIMARY KEY (question_id, skill_id)
);

CREATE INDEX IF NOT EXISTS idx_question_skill_skill_id ON question_skill(skill_id);
