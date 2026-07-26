# CLAUDE.md

Project context for Claude Code. Read this before making changes.

## What this is

An **IT interview knowledge base**: a single-user (no auth) service that stores interview questions
and answers, ingests
them from JSON, lists them by topic/tag, answers natural-language questions over them with an LLM
(RAG), and
supports human-in-the-loop merging of duplicate questions. A fourth module, `ai-view`, adds an
agentic chat UI (Azure OpenAI + tool-calling) for browsing questions by topic, asking factual
questions, matching a job description to existing questions, and generating quizzes.

## Guardrails

- **Never modify or remove an existing test to make it pass.** If a change you're making breaks a
  test,
  fix the underlying code, not the test — unless the test itself is asserting genuinely outdated
  behavior
  the user has explicitly asked you to change. Weakening assertions, deleting test cases, adding
  `@Disabled`/`@Ignore`, or loosening coverage thresholds to get a green build is reward hacking: it
  makes
  the build look healthy while hiding the actual regression. If you believe a test is wrong, say so
  and ask
  before touching it — don't silently delete or gut it.

## Stack (do not substitute)

- **Java 21**, **Spring Boot 3.4+**, **Spring AI 1.1.x** (pin the current patch from Maven Central).
- **PostgreSQL + pgvector** — one instance holds relational data *and* vectors. **Cloud: Supabase
  ** (connection
  configured in `application.yml`). No local PostgreSQL container.
- **Azure OpenAI** for embeddings (768-dim, via a reduced-dimension request on
  `text-embedding-3-small`) and chat (`gpt-4o` by default), accessed through Spring AI, in both
  `ingest-app` and `ai-view`. Neither module uses Ollama; `docker-compose.yml`'s `ollama` service
  is vestigial (kept only if useful for local experimentation).
- Spring Data JPA, Flyway, Jakarta Validation.

### Exact Spring AI artifacts (renamed at 1.0 — do not use old `*-spring-boot-starter` names)

- `org.springframework.ai:spring-ai-starter-model-azure-openai` (provides `ChatModel`/
  `ChatClient.Builder` + `EmbeddingModel`)
- `org.springframework.ai:spring-ai-starter-vector-store-pgvector` (provides the `VectorStore` bean)
- Managed by `org.springframework.ai:spring-ai-bom`.

## Architecture (Approach A — idiomatic Spring AI)

- Relational entities (`Topic`, `Question`, `Answer`, joins, `MergeLog`) are managed by **JPA**.
- Embeddings live in Spring AI's **`vector_store`** table via `PgVectorStore`. Each question is
  mirrored as a Spring AI
  `Document` whose **`id` equals the question UUID** (1:1), so deletes and lookups are trivial.
- `Question` has **no embedding column** — the vector lives in `vector_store`.
- Because `PgVectorStore` shares the same `DataSource`, relational + vector writes go in *
  *one `@Transactional` method**
  and are atomic. Every question insert/update/delete must keep both stores in sync.
- **`ingest-app` is the sole writer; `view-app` and `ai-view` are read-only.** All mutations — batch
  upsert (`/ingest`, `/interviews*`), merge (`/merge`), skill import, and single-question content
  edits
  (`/ingest/question/{id}` PATCH/POST, `/ingest/answers/{answerId}` PATCH) — live in
  `ingest-app`, so vector-store resync always happens alongside the relational write. `view-app`
  only
  reads (`QuestionRepository`/`SkillRepository` + `QuestionMapper` → `QuestionView`); it has no
  write
  endpoints and no edit UI. If a future change needs a new question/answer mutation, add it to
  `ingest-app`, not `view-app` or `ai-view`.
- **`ai-view` (port 8082) is a standalone Spring Boot app** with its own `ChatClient` +
  `PgVectorStore` wiring (mirroring `ingest-app`'s AI config, but `spring.flyway.enabled=false` /
  `ddl-auto=validate` like `view-app`, since it introduces no schema of its own). Both `ingest-app`
  and `ai-view` get their chat + embedding models from **Azure OpenAI**
  (`spring-ai-starter-model-azure-openai`, configured under `spring.ai.azure.openai.*` in each
  module's own `application.yml`) — the embedding deployment must request 768-dim output
  (`spring.ai.azure.openai.embedding.options.dimensions: 768`) to match the shared `vector_store`
  schema. `view-app` has no AI dependency of any kind (read-only, no RAG/chat). `ai-view`'s agent is
  a
  single `ChatClient` bean configured with `@Tool`-annotated methods
  (`chat/tools/QuestionBankTools`) — one tool per capability (topic search, knowledge-base Q&A, job
  description matching, single-skill quiz, quiz series). Spring AI's tool-calling loop handles
  multi-tool orchestration per turn automatically; tools only fetch grounding data; the model does
  the generative work. Conversation history is **in-memory and session-scoped**: a
  `MessageWindowChatMemory` bean (backed by `InMemoryChatMemoryRepository`) keyed by the HTTP
  session id via `MessageChatMemoryAdvisor` — no chat/conversation table exists or is needed.
- **Ingestion auto-merges near-duplicates in the background.** `QuestionUpsertService.upsert()`
  still returns synchronously (create/update rows, embed raw content) exactly as before, but for
  every genuinely *new* row it also schedules `QuestionDeduplicationService.deduplicateAsync(...)`
  to run once the enclosing transaction commits (via `TransactionSynchronizationManager
  .afterCommit`, not fired inline — a question queried on the background thread wouldn't be visible
  yet otherwise, especially since `/interviews` ingestion joins `QuestionUpsertService`'s
  transaction rather than opening its own). That background pass looks for an existing question
  that's a ≥0.95-similarity semantic match; if found, it rephrases the two questions into one
  (`prompts/question-merge-system.st` — grounded strictly in the two existing questions' own
  wording, no new information), reclassifies the merged question's level (via
  `QuestionLevelClassifier`, shared with the batch classification job below), and folds the new row
  into the match using `MergeService.merge(..., similarity, note)` (audit-logged, since this merge
  has no human review). Two brand-new questions in the *same* ingest batch that duplicate each
  other are deliberately not merged (see `QuestionDeduplicationService`'s class Javadoc) — a rarer
  edge case than what content-hash matching already catches. A match between 0.7 (matching
  `MergeController`'s own default `/merge/candidates` threshold) and 0.95 is too uncertain to
  auto-merge but too close to ignore — two questions can share a topic while asking for genuinely
  different things (e.g. "list the isolation levels" vs. "what anomaly does each prevent"), so
  these are logged and counted (`ingest.job.items{outcome=review_candidate}`) as candidates for the
  existing human-reviewed `/merge` flow rather than silently dropped.
- `QuestionLevelClassifier` (single-question classify call) is shared by both
  `QuestionLevelClassificationService` (the whole-table admin batch job) and
  `QuestionDeduplicationService` (ingestion-time auto-merge) — extend it, not either caller, if the
  classification prompt/logic itself needs to change.

## Conventions

- DTOs are Java **records**; entities are classes with `UUID` ids.
- View DTOs (e.g. `QuestionView`) don't inherit fields from their entity automatically — adding a
  field to an
  entity that a template needs means updating the record, its mapper (e.g. `QuestionMapper`), and
  any test
  that constructs the record positionally.
- **Constructor injection only** (no field injection). Prefer `final` fields.
- Service methods that touch both stores are `@Transactional`.
- **Flyway owns the entire schema**, including the `vector_store` table. Set
  `spring.jpa.hibernate.ddl-auto=validate`
  and `spring.ai.vectorstore.pgvector.initialize-schema=false`.
- Migrations live in `src/main/resources/db/migration` as `V<n>__<desc>.sql`. Never edit an applied
  migration; add a new
  one.
- `spring.jpa.open-in-view=false`.
- Keep controllers thin; logic in services.

## Package layout

```
io.github.posseidon.knowledgebase.it.interview
├── InterviewKbApplication.java
├── config/          # Spring AI beans, ChatClient builder
├── domain/          # JPA entities
├── repo/            # Spring Data repositories
├── dto/             # request/response + ingestion records
├── ingest/          # IngestionService, QuestionUpsertService, QuestionContentService
├── ask/             # AskService (RAG)
├── merge/           # MergeService (human-reviewed /merge flow)
├── dedup/           # QuestionDeduplicationService (automatic auto-merge-on-ingest, reuses MergeService)
├── vectorstore/      # QuestionDocuments (Question -> vector_store Document), VectorStoreReembed* admin job
├── classification/   # QuestionLevelClassification*/QuestionLevelClassifier (LLM skill-level classification)
├── metrics/          # LlmTokenMetrics — shared ingest.job.tokens bookkeeping for every LLM call site
└── web/             # REST controllers
```

Packages are split by domain, not by "everything ingestion-adjacent" — `vectorstore/` and
`classification/` used to live inside `ingest/` but were pulled out (SRP) once that package started
doing three unrelated things: core question ingestion, vector-store maintenance, and LLM
classification. `QuestionDocuments` (the `Question` → vector_store `Document` mapping) lives in
`vectorstore/` since that's its true domain, even though `ingest/` and `merge/` both call it.
`dedup/` is separate from `merge/` because it's a different trigger (automatic, no human review)
even though it calls into `MergeService` for the actual merge mechanics.

## Commands

```bash
mvn clean verify                  # compile + tests (all modules)
mvn -pl ingest-app spring-boot:run   # port 8081 — write/AI side, run at least once first (owns Flyway)
mvn -pl view-app spring-boot:run     # port 8080 — read-only browsing UI
mvn -pl ai-view spring-boot:run      # port 8082 — read-only agentic chat UI
mvn -pl view-app azure-webapp:deploy   # deploy view-app to the Azure Web App "ikb" (config in view-app/pom.xml)
```

`ingest-app` and `ai-view` both need `AZURE_OPEN_AI_KEY` set (no default) to start; `AZURE_OPEN_AI_ENDPOINT`,
`AZURE_OPEN_AI_DEPLOYMENT_NAME`, and `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` have working defaults in
each module's `application.yml` and only need overriding to point at a different endpoint/deployment.

`ai-view` has **no Azure Web App deployment config** — it isn't hosted on Azure, same as the other
two apps minus the `view-app`-specific `azure-webapp-maven-plugin` block. (It does call the Azure
OpenAI *API* for chat/embeddings — see above — which is a separate thing from Azure App Service
hosting.)

Health check: `GET /actuator/health` should be UP with DB (Supabase) reachable, plus Azure OpenAI
reachable (`ingest-app`, `ai-view`)
(`view-app`'s health check reports only `db`, since it has no AI dependency).

## Constraints / gotchas

- **Embedding dimension is 768** everywhere: the `vector(768)` column AND
  `spring.ai.vectorstore.pgvector.dimensions: 768`. A mismatch breaks index creation or returns
  silently-wrong
  results. (pgvector HNSW max is 2000 dims.)
- No authentication, no multi-tenancy — single user.
- Topic filtering during RAG is done **relationally** (intersect vector hits with a JPA query), not
  via vector-store
  metadata filter expressions.
- Merge is **destructive**: the source question is hard-deleted; its full snapshot is kept in
  `merge_log.source_snapshot`.
- "Most common questions in X" is driven by `Question.frequency`, incremented on each merge.
- `view-app` already has infra to deploy to an existing Azure Web App (`ikb`, resource group
  `west-eu-resource-group`, plan `asp-quizme`, West Europe, F1) via `azure-webapp-maven-plugin` —
  see
  `mvn -pl view-app azure-webapp:deploy` above. `DB_PASSWORD` has no default in `application.yml`
  and must
  already be set as an App Setting on the Azure Web App or the deployed instance won't start.

## Observability (Prometheus + Grafana)

- `ingest-app`'s admin background jobs (`VectorStoreReembedService`, `QuestionLevelClassificationService`
  — see below) publish Micrometer metrics under a single `ingest.job.*` family, tagged `job`
  (`vector_reembed` | `skill_level_classification`): `ingest.job.active` (gauge, 1 while running),
  `ingest.job.total`/`ingest.job.processed` (gauges, current run), `ingest.job.runs` (counter,
  tag `outcome=completed|failed`, one per finished run), `ingest.job.items` (counter, tag
  `outcome=succeeded|skipped|failed`, one per item), `ingest.job.call.duration` (timer, tag `step`),
  and `ingest.job.tokens` (counter, tag `type=prompt|completion|total`). `QuestionDeduplicationService`
  (ingestion-time auto-merge) reuses the same `ingest.job.items`/`call.duration`/`tokens` family under
  `job=question_dedup` (outcomes: `merged`/`no_match`/`failed`/`review_candidate` — the last one is a
  match too uncertain to auto-merge but flagged for the human-reviewed `/merge` flow; steps:
  `rephrase`, plus `classifyQuestion` shared with the classification job) — but has no `active`/`total`/`processed`
  gauges of its own, since unlike the other two it isn't a singleton whole-table job (each ingest
  call's pass only touches that call's own new rows, and multiple passes can run concurrently).
  Token-usage bookkeeping itself lives in one place, `LlmTokenMetrics`, shared by every LLM call site.
  Exposed at `GET /actuator/prometheus` (`management.endpoints.web.exposure.include` includes
  `prometheus`, `io.micrometer:micrometer-registry-prometheus` on the classpath).
- `docker compose up prometheus grafana` brings up Prometheus (`:9090`) + Grafana (`:3000`, login
  `admin`/`admin`) — config in `observability/`. **`ingest-app` itself is NOT containerized**; keep
  running it on the host (`mvn -pl ingest-app spring-boot:run`, port 8081) — Prometheus scrapes it
  via `host.docker.internal:8081/actuator/prometheus` from inside the compose network. Grafana
  auto-provisions a Prometheus datasource and an "Ingest Jobs" starter dashboard
  (`observability/grafana/dashboards/ingest-jobs.json`) on startup — no manual setup needed.

## Database (Supabase)

- Connection string and credentials live in `application.yml`. Do not add a local PostgreSQL service
  to
  `docker-compose.yml`.
- `spring.ai.vectorstore.pgvector.initialize-schema=false` — Flyway owns the schema, including
  `vector_store`.
- Supabase installs extensions in the `extensions` schema;
  `connection-init-sql: "SET search_path TO public, extensions"` ensures they are visible without
  schema qualification.

## Boy Scout Rule

If a change you make invalidates something this file says — a new command, a changed convention, a
new
gotcha, a moved package — update this file in the same change. Don't leave it for later; a stale
CLAUDE.md
costs every future session the time to rediscover what changed. Leave this file more accurate than
you
found it.
