# Transcript Orchestrator

The saga orchestrator for the Thai digital academic transcript pipeline. It drives each
transcript batch through two human approval gates (registrar → dean) and the automated
XML/PDF signing and rendering phases, coordinating worker services over Kafka with a
transactional outbox for reliable delivery and a sweeper for recovery.

Built as a hexagonal Spring Boot service (Java 21, port `8095`).

## The saga

A batch of transcripts flows through these states:

```
DRAFT ──close──▶ PENDING_REGISTRAR ──registrar approve──▶ REGISTRAR_SIGNING
   │                                                        │
   │                  (registrar XAdES XML sign)            ▼
   │                                               PENDING_DEAN ──dean approve──▶ DEAN_SIGNING
   │                                                                                 │
   │                                            (dean XAdES XML sign)               ▼
   │                                                                            SEALING ─▶ PDF_GENERATION
   │                                                                                          │
   │                                              (render PDF)                                 ▼
   │                                                                                  PDF_SIGNING ─▶ COMPLETED
   │
   └─▶ CANCELLED / FAILED   (terminal, alongside COMPLETED)
```

- **Human gates:** `PENDING_REGISTRAR` and `PENDING_DEAN` pause the saga until an approval event arrives.
- **Automatic phases** (`REGISTRAR_SIGNING`, `DEAN_SIGNING`, `PDF_GENERATION`, `PDF_SIGNING`) emit commands to worker services via the outbox and advance on their replies.
- A transcript can be **rejected** at a gate; if every item in a batch reaches a terminal state, the batch is cancelled.

## Tech stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.4, Spring Cloud 2024 |
| Messaging | Apache Camel 4 + Spring Kafka |
| Persistence | PostgreSQL, Flyway (`ddl-auto=validate`) |
| Object storage | AWS SDK v2 / Minio (S3-compatible) |
| Discovery | Eureka client |
| Observability | Spring Actuator + Micrometer/Prometheus |
| Domain contract | `com.wpanther:transcript-saga-commons:1.0.0-SNAPSHOT` |

## Prerequisites

- **JDK 21** and **Maven 3.9+**
- A locally installed `transcript-saga-commons` artifact (`mvn install` in the
  `transcript-saga-commons` project), or access to the snapshot repository.
- For running locally: a reachable **PostgreSQL**, **Kafka**, and **S3/Minio**.
  (Integration tests need none of these — they use Testcontainers.)

## Configuration

All runtime config is environment-overridable in `src/main/resources/application.yml`. Key variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `localhost`/`5432`/`transcript_orchestrator_db`/`postgres`/`postgres` | PostgreSQL connection |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_REGION` / `S3_XML_BUCKET` | Minio defaults | Object storage for XML/PDF artifacts |
| `KEYCLOAK_ISSUER` | `http://localhost:8080/realms/transcript` | Expected `iss` claim on every inbound JWT (browser-facing URL) |
| `KEYCLOAK_JWKS_URI` | `http://localhost:8080/realms/transcript/protocol/openid-connect/certs` | JWKS endpoint used to verify JWT signatures (in-network URL) |
| `CORS_ALLOWED_ORIGINS` | _(empty)_ | Comma-separated origins allowed by CORS for the browser SPA |
| `OUTBOX_RELAY_ENABLED` | `true` | Toggles the outbox drain relay |
| `STUCK_PHASE_TIMEOUT_MINUTES` / `SWEEPER_INTERVAL_MS` | `10` / `60000` | Recovery sweeper tuning |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Service registry |

## Build & run

```bash
# Unit tests only
mvn verify

# Unit + integration tests (spins up Postgres/Kafka/Minio via Testcontainers)
mvn clean verify -Pintegration

# Run locally (requires live Postgres, Kafka, S3/Minio)
mvn spring-boot:run
```

> If you hit `Unresolved compilation problems` at runtime, run `mvn clean` before rebuilding.

## REST API

Base path `/api/v1`. Every non-actuator request must carry a Keycloak-issued
JWT bearer token (`Authorization: Bearer <token>`). The token's `iss` is the
fixed string `KEYCLOAK_ISSUER`; signing keys are fetched from `KEYCLOAK_JWKS_URI`.
CORS for the browser UI is enabled via `CORS_ALLOWED_ORIGINS`. `/actuator/**`
is open.

### Batches — `/api/v1/batches`

| Method | Path | Body / Params | Notes |
|---|---|---|---|
| `POST` | `/` | `{ "name", "institutionCode", "createdBy" }` | Creates a `DRAFT` batch; returns `{ batchId, name, status }` (201) |
| `GET` | `/?status=` | optional `status` filter | Lists `BatchSummary` (capped at 100) |
| `GET` | `/{id}` | — | Full `BatchDetail` with items |
| `POST` | `/{id}/items` | `{ "itemIds": [uuid,…] }` | Assigns transcript items to the batch (204) |
| `DELETE` | `/{id}/items/{itemId}` | — | Unassigns an item (204) |
| `POST` | `/{id}/close` | header `X-Closed-By` | `DRAFT → PENDING_REGISTRAR` |

### Transcripts — `/api/v1/transcripts`

| Method | Path | Params | Notes |
|---|---|---|---|
| `GET` | `/` | `page`, `size` | Pool of unassigned transcript items |
| `GET` | `/{id}/xml` | — | Presigned S3 URL for the item's current XML artifact |

Error responses: `404` not found, `409` invalid state / empty batch, `403` institution mismatch.

## Kafka topology

Inbound consumers are Camel routes in `infrastructure/adapter/in/messaging/`; outbound
commands/replies/events are written to the outbox and relayed by `OutboxRelayRoute`.

| Topic | Direction | Purpose |
|---|---|---|
| `saga.commands.orchestrator` | in | Start a saga |
| `approval.registrar` / `approval.dean` | in | Human-gate approval decisions |
| `saga.reply.transcript-signing` | in | XAdES signing replies |
| `saga.reply.transcript-pdf-generation` | in | PDF render replies |
| `saga.command.transcript-signing.batch` | out | Batch XAdES signing command |
| `saga.command.transcript-pdf-generation` | out | PDF render command |
| `transcript.batch.completed` | out | Saga completion event |
| `transcript.orchestrator.dlq` | out | Dead-letter for failed inbound messages |

## Reliability mechanisms

- **Transactional outbox** — all outbound messages are written to `outbox_event` inside the
  originating DB transaction (`@Transactional` MANDATORY on adapters). `OutboxRelayRoute`
  (a 5-second Camel timer, gated by `OUTBOX_RELAY_ENABLED`) drains the table to Kafka.
- **Recovery sweeper** — `StuckPhaseSweeper` (scheduled at `SWEEPER_INTERVAL_MS`) re-drives
  automatic-phase batches that haven't progressed within `STUCK_PHASE_TIMEOUT_MINUTES`.
- **Optimistic locking** — `@Version` on `BatchEntity`; adapters use `saveAndFlush`.

## Project structure

```
src/main/java/com/wpanther/transcript/orchestrator
├── domain/                  # Pure model + BatchStateMachine (no Spring/JPA)
│   ├── model/  exception/  repository/  service/
├── application/             # Use cases + ports
│   ├── usecase/  dto/  port/out/
└── infrastructure/
    ├── adapter/in/          # web/ (REST), messaging/ (Camel Kafka routes)
    ├── adapter/out/         # persistence/  messaging/  storage/
    └── config/              # Properties, security, outbox wiring
```

## Testing

- **Domain tests** — pure unit tests of `BatchStateMachine` and model transitions.
- **Integration tests** (`-Pintegration`, `src/test/.../integration/`) — end-to-end saga
  coverage against Testcontainers Postgres/Kafka/Minio:
  `OrchestratorHappyPathIT`, `OrchestratorIdempotencyIT`,
  `OrchestratorInstitutionIsolationIT`, `OrchestratorRejectionIT`.

## Observability

Actuator endpoints (open, no auth): `health`, `info`, `metrics`, `prometheus` at
`/actuator/*`. Package logging at `DEBUG` for `com.wpanther.transcript.orchestrator`.
