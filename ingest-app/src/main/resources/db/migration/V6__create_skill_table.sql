CREATE TABLE skill (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name           TEXT NOT NULL,
    path           TEXT UNIQUE NOT NULL,
    description    TEXT,
    position_count INT,
    parent_id      UUID REFERENCES skill(id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_parent_id ON skill(parent_id);
