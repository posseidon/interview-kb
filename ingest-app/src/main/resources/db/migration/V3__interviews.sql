CREATE TABLE interview (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_code    TEXT NOT NULL,
    date            DATE NOT NULL,
    feedback        TEXT,
    upskilling_plan TEXT,
    decision        TEXT NOT NULL CHECK (decision IN ('NO_HIRE', 'MAYBE', 'GOOD_CANDIDATE')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE interview_question (
    interview_id UUID NOT NULL REFERENCES interview(id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    PRIMARY KEY (interview_id, question_id)
);

CREATE INDEX idx_interview_date ON interview(date ASC);
