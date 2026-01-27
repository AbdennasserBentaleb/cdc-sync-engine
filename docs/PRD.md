# Product Requirements Document

## Background

Analytics and search workloads sharing the same database as transactional operations frequently lead to resource contention at scale. Slow analytical queries exhaust connection pools and introduce lock contention, degrading the performance of the core application. 

A standard architectural pattern to resolve this is segregating read and write workloads. However, maintaining consistency between the primary database and a secondary read-optimized store introduces significant complexity when managed at the application layer.

## Objective

Design and implement an event-driven synchronization engine that maintains consistency between a primary PostgreSQL database and a secondary Elasticsearch cluster in near real-time, enforcing zero coupling with the upstream application writing to PostgreSQL.

## Requirements

### Functional

- Capture row-level `INSERT`, `UPDATE`, and `DELETE` mutations from the `orders` table in PostgreSQL.
- Translate captured database events into idempotent operations on an Elasticsearch index.
- Correctly parse the Debezium event envelope, handling complex payloads including tombstone events and initial snapshot reads.
- Execute full delete propagation to drop deleted PostgreSQL rows from the Elasticsearch read-model.
- Handle PostgreSQL-specific logical types (e.g. `DECIMAL`, `TIMESTAMP`) through appropriate serdes configuration and map them precisely to Java and Elasticsearch types.
- Provide a full-stack UI dashboard to visualize real-time Data Capture and Sync for demonstration purposes.

### Non-Functional

- **Latency**: End-to-end synchronization latency under 500ms from Postgres commit to Elasticsearch index availability.
- **Resilience**: The Kafka consumer must not crash on malformed events; errors must be logged, and partition consumption must advance smoothly.
- **Decoupling**: No schema modifications (e.g., specific `updated_at` triggers) or dual-write application logic required on the Postgres side.
- **Developer Experience**: The entire local infrastructure stack must be deployable via a single `docker compose` command.

### Out of Scope

- Multi-table CDC orchestration (scope is restricted to the `orders` table for this engine iteration).
- Authentication, Authorization, or TLS over Kafka/Elasticsearch (designed for local demonstration environments).
- Complex backfill strategies for historical records existing prior to connector instantiation (Debezium handles the initial snapshot payload execution natively).

## Technical Architecture Decisions

### Log-Based CDC vs. Application Polling

Application-level polling (e.g., querying `WHERE updated_at > last_sync`) is brittle. It necessitates schema pollution, introduces arbitrary read load onto the primary database, and completely misses hard deletes. By subscribing directly to the Postgres Write-Ahead Log (WAL) via Debezium, the engine incurs no supplementary querying load on the database while perfectly capturing every mutation, including physical deletes.

### Event Streaming Layer (Kafka)

Kafka introduces durable, replayable event streams. If the downstream consumer service suffers an outage, the mutation events safely accumulate within the topic partitions. Upon recovery, the consumer resumes from its last committed offset, guaranteeing eventual consistency without dropping events or requiring complex connector retries.

### Read Model (Elasticsearch)

Elasticsearch delivers highly optimized full-text search, aggregations, and arbitrary field sorting, making it the premier target for analytics and search read-models. While a read-only Postgres replica scales read throughput, Elasticsearch solves the fundamentally varied query access patterns that transactional databases struggle with.

### Service Implementation (Spring Boot)

Spring Kafka abstracts the low-level complexities of consumer group coordination, partition assignment, and offset management. Spring Data Elasticsearch provides repository abstractions over the underlying Elasticsearch client. The marginally heavier JVM footprint is a negligible tradeoff for a background synchronization daemon prioritizing reliability and maintainability.

## Verification & Acceptance Criteria

- **Infrastructure**: The entire dependent stack boots via `docker compose up`.
- **Synchronization**: Database inserts, updates, and deletes triggered against the primary Postgres instance are successfully reflected in the Elasticsearch index within a one-second SLA.
- **UI Observation**: The Vue.js demonstrator UI correctly renders real-time pipeline telemetry across both the source and the index.
- **Fault Tolerance**: The consumer daemon successfully bypasses malformed messages and cleanly ignores schema-less tombstone envelopes without crashing.
