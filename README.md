# Interview Knowledge Base

A single-user service for storing, searching, and querying IT interview questions and answers using a local LLM (RAG via Ollama) and PostgreSQL with pgvector.

## Stack

- Java 21 · Spring Boot 3.4 · Spring AI 1.1
- PostgreSQL 16 + pgvector via **Supabase** (cloud)
- Ollama (local) — `nomic-embed-text` for embeddings, `llama3.1:8b` for chat
- Flyway for schema migrations

---

## Running Locally

### Prerequisites

| Tool | macOS / Linux | Windows |
|------|---------------|---------|
| **Java 21** | `brew install --cask temurin@21` or download from [Adoptium](https://adoptium.net) | `winget install EclipseAdoptium.Temurin.21.JDK` or download from [Adoptium](https://adoptium.net) |
| **Maven 3.9+** | `brew install maven` | `winget install Apache.Maven` |
| **Docker** | [Docker Desktop](https://www.docker.com/products/docker-desktop/) or Colima | [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) (WSL 2 backend recommended) |

You also need a **Supabase** project with the `pgvector` extension enabled.

Verify your installs:

```bash
java -version   # should report 21+
mvn -version
docker version
```

### Step 1 — Configure the database

Open `src/main/resources/application.yml` and fill in your Supabase connection details:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<db-host>:5432/postgres
    username: postgres
    password: <your-supabase-db-password>
```

The host and password are available in your Supabase project under **Settings → Database → Connection string**.

### Step 2 — Start Ollama

```bash
docker compose up -d
```

This starts the Ollama container. The database runs on Supabase — no local PostgreSQL container is needed.

> **Windows note:** Run this command in PowerShell or Windows Terminal. Docker Desktop must be running first.

### Step 3 — Pull the embedding model (first time only)

```bash
ollama pull nomic-embed-text
```

The chat model (`llama3.1:8b`) is pulled automatically on first use. Pull it explicitly if you prefer:

```bash
ollama pull llama3.1:8b
```

> **Windows note:** If `ollama` is not found, the command runs inside the container. Use `docker exec ollama ollama pull nomic-embed-text` instead.

### Step 4 — Run the application

**macOS / Linux**
```bash
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
mvn spring-boot:run
```

Maven and the Spring Boot plugin work the same on both platforms. If `mvn` is not on your `PATH` after installation, restart your terminal or run `refreshenv` (Chocolatey) / open a new PowerShell window.

### Step 5 — Verify

**macOS / Linux**
```bash
curl http://localhost:8080/actuator/health
```

**Windows (PowerShell)**
```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected response:

```json
{"status": "UP"}
```

Both the `db` (Supabase) and Ollama components should report `UP`. If Ollama is still pulling a model, wait a moment and retry.

---

## REST Endpoints

Base URL: `http://localhost:8080`

### Ingestion

#### `POST /ingest`

Upserts questions, linking each question to existing skills by name. Idempotent — safe to call multiple times with the same data. Skills are not created here — they come from the skill catalog (`skills.xlsx` import); a name with no match is logged and the question is simply not linked to a skill for it. After each question is saved it is mirrored into the vector store.

```bash
curl -X POST http://localhost:8080/ingest \
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

---

### Skills

#### `GET /skills/{id}/questions`

Returns questions linked to a skill, ordered by frequency descending.

```bash
curl "http://localhost:8080/skills/3fa85f64-5717-4562-b3fc-2c963f66afa6/questions"

# With pagination
curl "http://localhost:8080/skills/3fa85f64-5717-4562-b3fc-2c963f66afa6/questions?page=0&size=10"
```

**Response `200`** — array of `QuestionView` (see schema below)

---

### Questions

#### `GET /questions`

Paginated listing with optional filters. Results are sorted by `frequency` desc by default.

| Param  | Type   | Description                          |
|--------|--------|--------------------------------------|
| `skill`  | uuid   | Filter by skill id                   |
| `q`      | string | Keyword search in question content   |
| `page`   | int    | Page number (0-based, default `0`)   |
| `size`   | int    | Page size (default `20`)             |
| `sort`   | string | Sort field and direction             |

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

### Ask (RAG)

#### `POST /ask`

Performs semantic search over the vector store, retrieves the most relevant questions and answers, and returns them as grounding sources. LLM synthesis is a stub and will be completed in a future iteration.

```bash
curl -X POST http://localhost:8080/ask \
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

---

### Merge (Human-in-the-Loop Deduplication)

#### `GET /merge/candidates`

Finds pairs of questions that are semantically similar above the given threshold. Use this to discover duplicate questions before merging.

| Param       | Type  | Description                               |
|-------------|-------|-------------------------------------------|
| `threshold` | float | Similarity threshold (default `0.7`, range `0.0–1.0`) |

```bash
# Default threshold (0.7)
curl "http://localhost:8080/merge/candidates"

# Higher threshold — fewer, more confident matches
curl "http://localhost:8080/merge/candidates?threshold=0.85"
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
curl -X POST http://localhost:8080/merge \
  -H "Content-Type: application/json" \
  -d '{
    "targetId": "9ba12a64-1234-4562-b3fc-2c963f66afa6",
    "sourceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  }'
```

**Response `204 No Content`** — merge successful

---

### Actuator

```bash
# Health (includes DB + Ollama status)
curl http://localhost:8080/actuator/health

# App info
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

`docker-compose.yml` runs Ollama only. The database is managed by Supabase — no local PostgreSQL container.

```bash
# Start Ollama
docker compose up -d

# Stop Ollama
docker compose down

# View Ollama logs
docker compose logs -f ollama
```

Database management is done via the [Supabase dashboard](https://supabase.com).
