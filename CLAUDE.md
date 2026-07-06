# CLAUDE.md

Project context for Claude Code. Read this before making changes.

## What this is

An **IT interview knowledge base**: a single-user (no auth) service that stores interview questions and answers, ingests them from JSON, lists them by topic/tag, answers natural-language questions over them with a local LLM (RAG), and supports human-in-the-loop merging of duplicate questions.

The current goal is to generate a **compiling, runnable skeleton** — full structure, wiring, and method signatures, with business logic stubbed as clearly-marked `// TODO` where noted in `PLAN.md`.

## Stack (do not substitute)

- **Java 21**, **Spring Boot 3.4+**, **Spring AI 1.1.x** (pin the current patch from Maven Central).
- **PostgreSQL + pgvector** — one instance holds relational data *and* vectors. Local: `pgvector/pgvector:pg16` via Docker. Cloud testing: Neon or Supabase free tier.
- **Ollama** (local) for embeddings (`nomic-embed-text`, 768-dim) and chat (`llama3.1:8b` or `qwen2.5:7b`), accessed through Spring AI.
- Spring Data JPA, Flyway, Jakarta Validation.

### Exact Spring AI artifacts (renamed at 1.0 — do not use old `*-spring-boot-starter` names)

- `org.springframework.ai:spring-ai-starter-model-ollama` (provides `ChatModel`/`ChatClient.Builder` + `EmbeddingModel`)
- `org.springframework.ai:spring-ai-starter-vector-store-pgvector` (provides the `VectorStore` bean)
- Managed by `org.springframework.ai:spring-ai-bom`.

## Architecture (Approach A — idiomatic Spring AI)

- Relational entities (`Topic`, `Question`, `Answer`, `Tag`, joins, `MergeLog`) are managed by **JPA**.
- Embeddings live in Spring AI's **`vector_store`** table via `PgVectorStore`. Each question is mirrored as a Spring AI `Document` whose **`id` equals the question UUID** (1:1), so deletes and lookups are trivial.
- `Question` has **no embedding column** — the vector lives in `vector_store`.
- Because `PgVectorStore` shares the same `DataSource`, relational + vector writes go in **one `@Transactional` method** and are atomic. Every question insert/update/delete must keep both stores in sync.

## Conventions

- DTOs are Java **records**; entities are classes with `UUID` ids.
- **Constructor injection only** (no field injection). Prefer `final` fields.
- Service methods that touch both stores are `@Transactional`.
- **Flyway owns the entire schema**, including the `vector_store` table. Set `spring.jpa.hibernate.ddl-auto=validate` and `spring.ai.vectorstore.pgvector.initialize-schema=false`.
- Migrations live in `src/main/resources/db/migration` as `V<n>__<desc>.sql`. Never edit an applied migration; add a new one.
- `spring.jpa.open-in-view=false`.
- Keep controllers thin; logic in services.

## Package layout

```
io.github.posseidon.knowledgebase.it.interview
├── InterviewKbApplication.java
├── config/      # Spring AI beans, ChatClient builder
├── domain/      # JPA entities
├── repo/        # Spring Data repositories
├── dto/         # request/response + ingestion records
├── ingest/      # IngestionService
├── ask/         # AskService (RAG)
├── merge/       # MergeService
└── web/         # REST controllers
```

## Commands

```bash
docker compose up -d          # Postgres(pgvector) + Ollama
ollama pull nomic-embed-text  # if not auto-pulled
mvn clean verify              # compile + tests
mvn spring-boot:run           # run the app
```

Health check: `GET /actuator/health` should be UP with DB + Ollama reachable.

## Constraints / gotchas

- **Embedding dimension is 768** everywhere: the `vector(768)` column AND `spring.ai.vectorstore.pgvector.dimensions: 768`. A mismatch breaks index creation or returns silently-wrong results. (pgvector HNSW max is 2000 dims.)
- No authentication, no multi-tenancy — single user.
- Topic/tag filtering during RAG is done **relationally** (intersect vector hits with a JPA query), not via vector-store metadata filter expressions.
- Merge is **destructive**: the source question is hard-deleted; its full snapshot is kept in `merge_log.source_snapshot`.
- "Most common questions in X" is driven by `Question.frequency`, incremented on each merge.

## Definition of done (skeleton)

App starts, Flyway applies `V1__init.sql`, `/actuator/health` is UP, all layers exist and compile, the ingestion happy path stores a question in both stores, and `mvn verify` passes. Deep logic in `AskService` and `MergeService` may remain `// TODO` per `PLAN.md`.
