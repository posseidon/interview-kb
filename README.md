# Interview Knowledge Base

A single-user service for storing, searching, and querying IT interview questions and answers using a local LLM (RAG via Ollama) and PostgreSQL with pgvector.

## Stack

- Java 21 · Spring Boot 3.4 · Spring AI 1.1
- PostgreSQL 16 + pgvector via **Supabase** (cloud)
- Ollama (local) — `nomic-embed-text` for embeddings, `llama3.1:8b` for chat
- Flyway for schema migrations

---

## Quick Start

```bash
# 1. Start Ollama (database is Supabase — no local DB needed)
docker compose up -d

# 2. Pull embedding model (first time only)
ollama pull nomic-embed-text

# 3. Run the app
mvn spring-boot:run
```

Health check:
```bash
curl http://localhost:8080/actuator/health
```

---

## REST Endpoints

Base URL: `http://localhost:8080`

### Ingestion

#### `POST /ingest`

Upserts topics, tags, and questions. Idempotent — safe to call multiple times with the same data. After each question is saved it is mirrored into the vector store.

```bash
curl -X POST http://localhost:8080/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "topics": [
      {
        "slug": "kafka",
        "name": "Kafka",
        "description": "Event streaming and message broker concepts"
      }
    ],
    "questions": [
      {
        "external_id": "kafka-topic-vs-partition",
        "content": "What is a topic? What is a partition?",
        "requires_impl": false,
        "language": "en",
        "topics": ["kafka"],
        "tags": ["fundamentals", "architecture"],
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
  "topicsCreated": 1,
  "topicsUpdated": 0,
  "tagsCreated": 2,
  "tagsUpdated": 0,
  "questionsCreated": 1,
  "questionsUpdated": 0,
  "answersAdded": 1
}
```

---

### Topics

#### `GET /topics`

Returns all topics.

```bash
curl http://localhost:8080/topics
```

**Response `200`**
```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "slug": "kafka",
    "name": "Kafka",
    "description": "Event streaming and message broker concepts"
  }
]
```

#### `GET /topics/{slug}/questions`

Returns questions for a topic, ordered by frequency descending.

```bash
curl "http://localhost:8080/topics/kafka/questions"

# With pagination
curl "http://localhost:8080/topics/kafka/questions?page=0&size=10"
```

**Response `200`** — array of `QuestionView` (see schema below)

---

### Tags

#### `GET /tags`

Returns all tags.

```bash
curl http://localhost:8080/tags
```

**Response `200`**
```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "fundamentals"
  }
]
```

---

### Questions

#### `GET /questions`

Paginated listing with optional filters. Results are sorted by `frequency` desc by default.

| Param  | Type   | Description                          |
|--------|--------|--------------------------------------|
| `topic`  | string | Filter by topic slug                 |
| `tag`    | string | Filter by tag name                   |
| `q`      | string | Keyword search in question content   |
| `page`   | int    | Page number (0-based, default `0`)   |
| `size`   | int    | Page size (default `20`)             |
| `sort`   | string | Sort field and direction             |

```bash
# All questions
curl "http://localhost:8080/questions"

# Filter by topic
curl "http://localhost:8080/questions?topic=kafka"

# Filter by tag
curl "http://localhost:8080/questions?tag=fundamentals"

# Keyword search
curl "http://localhost:8080/questions?q=partition"

# Combined filters with pagination
curl "http://localhost:8080/questions?topic=kafka&tag=fundamentals&q=partition&page=0&size=10"

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
      "topics": ["kafka"],
      "tags": ["fundamentals", "architecture"],
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
      "topics": ["kafka"],
      "tags": ["fundamentals", "architecture"],
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
- Topics and tags are unioned onto the target
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

Returned by `/questions`, `/questions/{id}`, `/topics/{slug}/questions`, and `/ask`.

```json
{
  "id": "uuid",
  "externalId": "string | null",
  "content": "string",
  "requiresImpl": "boolean",
  "language": "string",
  "frequency": "integer",
  "topics": ["slug1", "slug2"],
  "tags": ["tag1", "tag2"],
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
