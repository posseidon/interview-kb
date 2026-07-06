CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Spring AI PgVectorStore table (must match its expected shape)
CREATE TABLE vector_store (
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(768)
);
CREATE INDEX idx_vs_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE TABLE topic (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug        TEXT UNIQUE NOT NULL,
    name        TEXT NOT NULL,
    description TEXT,
    parent_id   UUID REFERENCES topic(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE question (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id   TEXT UNIQUE,
    content       TEXT NOT NULL,
    content_hash  TEXT NOT NULL,
    requires_impl BOOLEAN NOT NULL DEFAULT FALSE,
    language      TEXT,
    frequency     INT NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE answer (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    question_id  UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    content      TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    source       TEXT NOT NULL DEFAULT 'human',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tag (
    id   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT UNIQUE NOT NULL
);

CREATE TABLE question_topic (
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    topic_id    UUID NOT NULL REFERENCES topic(id)    ON DELETE CASCADE,
    PRIMARY KEY (question_id, topic_id)
);

CREATE TABLE question_tag (
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tag(id)      ON DELETE CASCADE,
    PRIMARY KEY (question_id, tag_id)
);

CREATE TABLE merge_log (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    into_question_id UUID REFERENCES question(id) ON DELETE SET NULL,
    source_snapshot  JSONB NOT NULL,
    similarity       REAL,
    note             TEXT,
    merged_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_question_content_trgm ON question USING gin (content gin_trgm_ops);
CREATE INDEX idx_qt_topic ON question_topic(topic_id);
CREATE INDEX idx_qtag_tag ON question_tag(tag_id);
