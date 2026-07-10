# PLAN.md — Build the project skeleton

Goal: scaffold a **compiling, runnable Spring Boot + Spring AI skeleton** for the interview
knowledge base. Work top to
bottom; check items off as you go. Read `CLAUDE.md` first. Deep business logic may be left as
`// TODO` where marked —
but the project must **compile, start, pass `mvn verify`, and report `/actuator/health` UP**.

---

## Phase 0 — Project + infrastructure

- [ ] Generate a Spring Boot 3.4+ project, group `com.interviewkb`, Java 21, packaging `jar`.
- [ ] Create `pom.xml` with the Spring AI BOM and dependencies below. Pin `spring-ai.version` to the
  current `1.1.x`
  from Maven Central.

```xml
<properties>
  <java.version>21</java.version>
  <spring-ai.version>1.1.0</spring-ai.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>${spring-ai.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>

  <dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-starter-model-ollama</artifactId></dependency>
  <dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-starter-vector-store-pgvector</artifactId></dependency>

  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
</dependencies>
```

- [ ] Create `docker-compose.yml` with two services:
    - `db`: image `pgvector/pgvector:pg16`, env `POSTGRES_DB=interview_kb`,
      `POSTGRES_USER=postgres`,
      `POSTGRES_PASSWORD=postgres`, port `5432:5432`, a named volume.
    - `ollama`: image `ollama/ollama`, port `11434:11434`, a named volume for models.
- [ ] Create `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/interview_kb
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  ai:
    ollama:
      base-url: http://localhost:11434
      init:
        pull-model-strategy: when_missing
      chat:
        options:
          model: llama3.1:8b
      embedding:
        options:
          model: nomic-embed-text
    vectorstore:
      pgvector:
        initialize-schema: false
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 768

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

**Done when:** `mvn compile` succeeds and `docker compose up -d` brings up both services.

---

## Phase 1 — Database schema (Flyway)

- [ ] Create `src/main/resources/db/migration/V1__init.sql` exactly as below (Flyway owns the whole
  schema, including
  Spring AI's `vector_store`).

```sql
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
```

**Done when:** the app starts and Flyway applies `V1` cleanly against the Dockerized DB.

---

## Phase 2 — Domain entities + repositories

- [ ] In `domain/`, create JPA entities (classes, `UUID` ids, `@GeneratedValue`):
    - `Topic` (slug, name, description, optional self-ref `parent`).
    - `Question` (externalId, content, contentHash, requiresImpl, language, frequency; `@ManyToMany`
      to `Topic` via
      `question_topic`; `@ManyToMany` to `Tag` via `question_tag`; `@OneToMany` to `Answer`). **No
      embedding field.**
    - `Answer` (`@ManyToOne` question, content, contentHash, source).
    - `Tag` (name).
    - `MergeLog` (intoQuestionId, `source_snapshot` as JSONB string/`@JdbcTypeCode(SqlTypes.JSON)`,
      similarity, note,
      mergedAt).
- [ ] In `repo/`, create Spring Data repositories: `TopicRepository` (findBySlug),
  `QuestionRepository` (
  findByExternalId, findByContentHash, paginated filters by topic slug + tag name + keyword),
  `AnswerRepository`,
  `TagRepository` (findByName), `MergeLogRepository`.

**Done when:** `mvn test` passes a smoke test that saves and reads a `Question` with topics, tags,
and answers, and
`ddl-auto: validate` agrees with the Flyway schema.

---

## Phase 3 — DTOs + ingestion

- [ ] In `dto/`, create ingestion records matching the JSON contract:
    - `IngestRequest(List<TopicDto> topics, List<QuestionDto> questions)`
    - `TopicDto(String slug, String name, String description)`
    -
  `QuestionDto(String externalId, String content, boolean requiresImpl, String language, List<String> topics, List<String> tags, List<AnswerDto> answers)`
    - `AnswerDto(String source, String content)`
    - Response records for listing/ask (e.g. `QuestionView`,
      `AskResponse(String answer, List<QuestionView> sources)`).
- [ ] In `ingest/IngestionService` (`@Transactional`): a `ContentHash` helper (SHA-256 of normalized
  text); upsert
  topics by slug and tags by name (auto-create); upsert questions by `externalId` then
  `contentHash`; skip answers whose
  `contentHash` already exists on the question. After saving each question, mirror it into the
  vector store:
  ```java
  vectorStore.add(List.of(Document.builder()
      .id(question.getId().toString())
      .text(question.getContent())
      .metadata(Map.of("topics", topicSlugs, "tags", tagNames, "frequency", question.getFrequency()))
      .build()));
  ```
  On content change, `vectorStore.delete(List.of(id))` then re-add.

**Done when:** ingesting the sample JSON twice is idempotent and `vector_store` has exactly one row
per question.

### JSON contract (reference)

```json
{
  "topics": [
    { "slug": "kafka", "name": "Kafka", "description": "Event streaming basics." }
  ],
  "questions": [
    {
      "external_id": "kafka-topic-vs-partition",
      "content": "What is a topic? What is a partition?",
      "requires_impl": false,
      "topics": ["kafka"],
      "tags": ["kafka", "fundamentals"],
      "answers": [ { "source": "human", "content": "A topic is an append-only log; partitions are the unit of parallelism and ordering." } ]
    }
  ]
}
```

---

## Phase 4 — Controllers + structured listing

- [ ] In `web/`, create REST controllers (thin; delegate to services):
    - `POST /ingest` → `IngestionService`, returns created/updated counts.
    - `GET /topics`, `GET /tags`.
    - `GET /questions?topic=&tag=&q=&page=&size=&sort=` → paginated listing with answers.
    - `GET /questions/{id}` → full question view.
    - `GET /topics/{slug}/questions?sort=frequency,desc`.
- [ ] Implement the listing queries in `QuestionRepository` / a `QuestionQueryService`.

**Done when:** listing by topic + tag + keyword returns expected rows with their answers.

---

## Phase 5 — Ask (RAG) — wiring + stubs

- [ ] In `config/`, expose a `ChatClient` bean from the injected `ChatClient.Builder`.
- [ ] In `ask/AskService`, implement the pipeline shell:
    1. (Optional, `// TODO`) extract `{topics, tags, semanticQuery}` via a `ChatClient` JSON call.
    2.
  `vectorStore.similaritySearch(SearchRequest.builder().query(semanticQuery).topK(8).similarityThreshold(0.4).build())`.
    3. Map `Document::getId` → question UUIDs; load entities via `QuestionRepository`; constrain by
       topics/tags
       relationally; order by similarity (and by `frequency` for "most common" intent).
    4. (`// TODO`) call `ChatClient` with the question + retrieved Q&A as grounding context.
    5. Return `AskResponse(answer, sources)`.
- [ ] `POST /ask` controller.

**Done when:** `/ask` returns the retrieved source questions for a query (LLM synthesis step may
stay `// TODO`), with
no startup or wiring errors.

---

## Phase 6 — Merge (human-in-the-loop) — wiring + stubs

- [ ] In `merge/MergeService`:
    - `findCandidates(threshold)`: for each question, `similaritySearch` on its own text (small
      `topK`, high threshold),
      excluding itself → candidate pairs with scores.
    - `merge(targetId, sourceId)` (`@Transactional`): snapshot source into `merge_log`; re-point
      source answers to
      target; union topics + tags onto target; `target.frequency += source.frequency`;
      `vectorStore.delete(sourceId)`
      and re-add target document with refreshed metadata; hard-delete the source question.
- [ ] `GET /merge/candidates?threshold=`, `POST /merge` controllers.

**Done when:** approving a merge moves answers to the target, unions topics/tags, increments
`frequency`, writes a
`merge_log` row, deletes the source row, and removes the source's vector document — all atomically.

---

## Phase 7 — Tests + verification

- [ ] Add a Testcontainers integration test: `pgvector/pgvector:pg16` via `@ServiceConnection`; stub
  or mock
  `EmbeddingModel`/`ChatModel` so tests don't require a running Ollama. Cover: Flyway boots,
  ingestion happy path writes
  both stores, listing returns rows.
- [ ] Ensure `mvn verify` is green and `/actuator/health` is UP when run against Docker.

**Done when:** the skeleton compiles, starts, passes `mvn verify`, and exposes all endpoints (deep
RAG/merge logic may
remain `// TODO`).

---

## Out of scope for the skeleton

- Full LLM synthesis prompt tuning in `AskService`.
- A browse/review UI or CLI.
- Generating the full `interview-seed.json` from the source PDF (separate task).
- Authentication (intentionally none).
