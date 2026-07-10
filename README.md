# Interview Knowledge Base

A single-user service for storing, searching, and browsing IT interview questions and answers, with
semantic search and
human-in-the-loop merging backed by a local LLM (RAG via Ollama) and PostgreSQL with pgvector.

The project is a Maven multi-module build split into three modules:

```
interview-kb/
├── shared/       Domain model, DTOs, repositories — no web layer, no AI
├── ingest-app/   Ingestion, RAG (/ask), merge, skill import — Ollama + pgvector
└── view-app/     Browsing UI, basket, interviews — plain read/write, no AI
```

## Modules

### `shared`

Not a runnable app — a library jar consumed by both `ingest-app` and `view-app`.

- JPA entities (`Question`, `Answer`, `Skill`, `Interview`, `Decision`, `MergeLog`), organized under
  `domain/<feature>`.
- DTOs, organized under `dto/<feature>` (e.g. `dto/ingest/request`, `dto/ingest/response`,
  `dto/question`,
  `dto/interview`, `dto/ask`).
- Spring Data repositories (`repo/`) and shared utilities (`util/`, e.g. `ContentHash`, `Markdown`,
  `QuestionMapper`).
- Flyway migrations (`db/migration`) — **owned by this module's schema, applied by `ingest-app`** (
  see below).

### `ingest-app` — port `8081`

The write/AI side: everything that talks to Ollama or touches the vector store.

- **Ingestion** — `POST /ingest`, `POST /ingest/questions` (AI-assisted markdown ingestion).
- **Skill import** — `POST /skills/import` (upload `skills.xlsx` to (re)build the skill catalog).
- **Ask (RAG)** — `POST /ask` — semantic search over the vector store.
- **Interview ingestion** — `POST /interviews`, `POST /interviews/questions`.
- **Merge** — `GET /merge/candidates`, `POST /merge` — human-in-the-loop duplicate
  detection/merging.
- Owns the schema: `spring.flyway.enabled=true` here; `view-app` only validates against it.

### `view-app` — port `8080`

The read/browse side: the Thymeleaf UI plus a small JSON API, no AI dependency.

- **Handbook UI** — home, search/ask results, question detail with markdown rendering and inline
  answer editing.
- **Skills UI** — browsable skill tree with level pickers (`/skills`, `/skills/{id}`).
- **Basket** — collect skills at a level and check out (`/basket/*`).
- **Interviews UI** — list and detail views for recorded interviews.
- **JSON API** — `GET /questions`, `GET /questions/{id}`, `GET /skills/{id}/questions` for
  programmatic access to the
  same data.
- `spring.flyway.enabled=false` — this module never migrates the schema, only validates its entities
  against it (
  `ddl-auto=validate`).

Both apps connect to the same Supabase Postgres instance and share the `shared` module's
entities/repositories, so data
written by `ingest-app` is immediately visible in `view-app`.

---

## Stack

- Java 21 · Spring Boot 3.4 · Spring AI 1.1 (`ingest-app` only)
- PostgreSQL 16 + pgvector via **Supabase** (cloud) — shared by both apps
- Ollama (local) — `nomic-embed-text` for embeddings, `llama3.1:8b` for chat — used by `ingest-app`
  only
- Flyway for schema migrations (applied by `ingest-app`)
- Thymeleaf for server-rendered views (`view-app` only)

---

## Running Locally

### Prerequisites

| Tool           | macOS / Linux                                                                      | Windows                                                                                                   |
|----------------|------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| **Java 21**    | `brew install --cask temurin@21` or download from [Adoptium](https://adoptium.net) | `winget install EclipseAdoptium.Temurin.21.JDK` or download from [Adoptium](https://adoptium.net)         |
| **Maven 3.9+** | `brew install maven`                                                               | `winget install Apache.Maven`                                                                             |
| **Docker**     | [Docker Desktop](https://www.docker.com/products/docker-desktop/) or Colima        | [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) (WSL 2 backend recommended) |

You also need a **Supabase** project with the `pgvector` extension enabled.

Verify your installs:

```bash
java -version   # should report 21+
mvn -version
docker version
```

### Step 1 — Configure the database

Both `ingest-app/src/main/resources/application.yml` and
`view-app/src/main/resources/application.yml` read the same
environment variables:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://<db-host>:5432/postgres}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
```

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in your shell (or override the defaults directly in
the YAML). The host,
username, and password are available in your Supabase project under **Settings → Database →
Connection string**.

### Step 2 — Start Ollama

```bash
docker compose up -d
```

This starts the Ollama container. The database runs on Supabase — no local PostgreSQL container is
needed. Only
`ingest-app` talks to Ollama; `view-app` does not need it running.

> **Windows note:** Run this command in PowerShell or Windows Terminal. Docker Desktop must be
> running first.

### Step 3 — Pull the embedding model (first time only)

```bash
ollama pull nomic-embed-text
```

The chat model (`llama3.1:8b`) is pulled automatically on first use. Pull it explicitly if you
prefer:

```bash
ollama pull llama3.1:8b
```

> **Windows note:** If `ollama` is not found, the command runs inside the container. Use
`docker exec ollama ollama pull nomic-embed-text` instead.

### Step 4 — Build

From the repo root, build all three modules once:

```bash
mvn clean install
```

### Step 5 — Run the apps

The two apps are independent Spring Boot processes and can be started separately, in either order. *
*Start `ingest-app`
first at least once** — it owns the Flyway migrations, so the schema needs to exist before
`view-app` (which only
validates it) connects.

**Ingestion app — port 8081**

```bash
mvn -pl ingest-app -am spring-boot:run
```

**View app — port 8080**

```bash
mvn -pl view-app -am spring-boot:run
```

Run both in separate terminals to have the full system (ingest/AI + browsing UI) available at once.

### Step 6 — Verify

**macOS / Linux**

```bash
curl http://localhost:8081/actuator/health   # ingest-app
curl http://localhost:8080/actuator/health   # view-app
```

**Windows (PowerShell)**

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected response from each:

```json
{"status": "UP"}
```

`ingest-app`'s health check reports both `db` (Supabase) and Ollama as `UP`. `view-app`'s reports
only `db`, since it
has no AI dependency. If Ollama is still pulling a model, wait a moment and retry.

Once both are up, open `http://localhost:8080/` in a browser for the handbook UI.

---

## REST Endpoints

### `ingest-app` — `http://localhost:8081`

#### `POST /ingest`

Upserts questions, linking each question to existing skills by name. Idempotent — safe to call
multiple times with the
same data. Skills are not created here — they come from the skill catalog (`skills.xlsx` import via
`/skills/import`); a
name with no match is logged and the question is simply not linked to a skill for it. After each
question is saved it is
mirrored into the vector store.

```bash
curl -X POST http://localhost:8081/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "questions": [
      {
        "external_id": "kafka-topic-vs-partition",
        "content": "What is a topic? What is a partition?",
        "requires_impl": false,
        "language": "en",
        "skills": ["Kafka"],
        "answers": [
          {
            "source": "human",
            "content": "A topic is an append-only log; partitions are the unit of parallelism and ordering."
          }
        ]
      }
    ]
  }'
```

**Response `200`**

```json
{
  "questionsCreated": 1,
  "questionsUpdated": 0,
  "answersAdded": 1
}
```

#### `POST /ingest/questions`

AI-assisted ingestion: takes raw markdown-formatted questions and lets the LLM structure and answer
them before saving.

#### `POST /skills/import`

Uploads a `skills.xlsx` workbook to (re)build the skill catalog used to resolve `skills: [...]`
names during ingestion.

```bash
curl -X POST http://localhost:8081/skills/import -F "file=@skills.xlsx"
```

**Response `200`**

```json
{"imported": 42}
```

#### `POST /ask`

Performs semantic search over the vector store, retrieves the most relevant questions and answers,
and returns them as
grounding sources.

```bash
curl -X POST http://localhost:8081/ask \
  -H "Content-Type: application/json" \
  -d '{"query": "How does Kafka handle message ordering?"}'
```

**Response `200`**

```json
{
  "answer": "[Stub: LLM synthesis not yet implemented]",
  "sources": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "externalId": "kafka-topic-vs-partition",
      "content": "What is a topic? What is a partition?",
      "requiresImpl": false,
      "language": "en",
      "frequency": 1,
      "skills": [{"id": "c1d2e3f4-1234-4562-b3fc-2c963f66afa6", "name": "Kafka"}],
      "answers": [
        {
          "id": "7fa12a64-1234-4562-b3fc-2c963f66afa6",
          "source": "human",
          "content": "A topic is an append-only log; partitions are the unit of parallelism and ordering."
        }
      ]
    }
  ]
}
```

#### `POST /interviews`, `POST /interviews/questions`

Ingests a recorded interview (project code, decision, questions asked) for later browsing in
`view-app`.

#### `GET /merge/candidates`

Finds pairs of questions that are semantically similar above the given threshold. Use this to
discover duplicate
questions before merging.

| Param       | Type  | Description                                           |
|-------------|-------|-------------------------------------------------------|
| `threshold` | float | Similarity threshold (default `0.7`, range `0.0–1.0`) |

```bash
curl "http://localhost:8081/merge/candidates?threshold=0.85"
```

**Response `200`**

```json
[
  {
    "sourceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "targetId": "9ba12a64-1234-4562-b3fc-2c963f66afa6",
    "similarity": 0.91
  }
]
```

#### `POST /merge`

Merges `sourceId` into `targetId`. This is **destructive and irreversible**:

- Source answers are moved to the target
- Skills are unioned onto the target
- `target.frequency += source.frequency`
- Source is deleted from both the relational store and the vector store
- A snapshot of the source is saved to `merge_log` for audit

```bash
curl -X POST http://localhost:8081/merge \
  -H "Content-Type: application/json" \
  -d '{
    "targetId": "9ba12a64-1234-4562-b3fc-2c963f66afa6",
    "sourceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  }'
```

**Response `204 No Content`** — merge successful

---

### `view-app` — `http://localhost:8080`

Most of `view-app` is server-rendered HTML (home `/`, search `/search`, skills `/skills`,
`/skills/{id}`, question
detail `/questions/{id}`, basket `/basket`, interviews `/interviews`). The JSON endpoints below are
the same read paths
used programmatically.

#### `GET /skills/{id}/questions`

Returns questions linked to a skill, ordered by frequency descending.

```bash
curl "http://localhost:8080/skills/3fa85f64-5717-4562-b3fc-2c963f66afa6/questions"

# With pagination
curl "http://localhost:8080/skills/3fa85f64-5717-4562-b3fc-2c963f66afa6/questions?page=0&size=10"
```

**Response `200`** — array of `QuestionView` (see schema below)

#### `GET /questions`

Paginated listing with optional filters. Results are sorted by `frequency` desc by default.

| Param   | Type   | Description                        |
|---------|--------|------------------------------------|
| `skill` | uuid   | Filter by skill id                 |
| `q`     | string | Keyword search in question content |
| `page`  | int    | Page number (0-based, default `0`) |
| `size`  | int    | Page size (default `20`)           |
| `sort`  | string | Sort field and direction           |

```bash
# All questions
curl "http://localhost:8080/questions"

# Filter by skill
curl "http://localhost:8080/questions?skill=3fa85f64-5717-4562-b3fc-2c963f66afa6"

# Keyword search
curl "http://localhost:8080/questions?q=partition"

# Combined filters with pagination
curl "http://localhost:8080/questions?skill=3fa85f64-5717-4562-b3fc-2c963f66afa6&q=partition&page=0&size=10"

# Sort by creation date descending
curl "http://localhost:8080/questions?sort=createdAt,desc"
```

**Response `200`**

```json
{
  "content": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "externalId": "kafka-topic-vs-partition",
      "content": "What is a topic? What is a partition?",
      "requiresImpl": false,
      "language": "en",
      "frequency": 1,
      "skills": [{"id": "c1d2e3f4-1234-4562-b3fc-2c963f66afa6", "name": "Kafka"}],
      "answers": [
        {
          "id": "7fa12a64-1234-4562-b3fc-2c963f66afa6",
          "source": "human",
          "content": "A topic is an append-only log; partitions are the unit of parallelism and ordering."
        }
      ]
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

#### `GET /questions/{id}`

Returns a single question by UUID.

```bash
curl http://localhost:8080/questions/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Response `200`** — single `QuestionView`
**Response `404`** — question not found

---

### Actuator (both apps)

```bash
# Health (ingest-app also reports Ollama status)
curl http://localhost:8081/actuator/health
curl http://localhost:8080/actuator/health

# App info
curl http://localhost:8081/actuator/info
curl http://localhost:8080/actuator/info
```

---

## QuestionView Schema

Returned by `/questions`, `/questions/{id}`, `/skills/{id}/questions`, and `/ask`.

```json
{
  "id": "uuid",
  "externalId": "string | null",
  "content": "string",
  "requiresImpl": "boolean",
  "language": "string",
  "frequency": "integer",
  "skills": [{"id": "uuid", "name": "string"}],
  "answers": [
    {
      "id": "uuid",
      "source": "human | ai",
      "content": "string"
    }
  ]
}
```

---

## Infrastructure

`docker-compose.yml` runs Ollama only. The database is managed by Supabase — no local PostgreSQL
container.

```bash
# Start Ollama
docker compose up -d

# Stop Ollama
docker compose down

# View Ollama logs
docker compose logs -f ollama
```

Database management is done via the [Supabase dashboard](https://supabase.com).
