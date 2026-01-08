# CDC Sync Engine

A Change Data Capture (CDC) synchronization mechanism using Debezium, Apache Kafka, and Elasticsearch.

This project implements the extraction of row-level mutations from a PostgreSQL Write-Ahead Log (WAL) to incrementally synchronize a downstream Elasticsearch read-model. It demonstrates a common pattern for decoupling search/analytics workloads from the primary transactional database without introducing synchronous dual-writes.

## System Architecture

The pipeline utilizes **Debezium** to ingest the Postgres WAL, capturing `INSERT`, `UPDATE`, and `DELETE` events from the source database.

These events are published via Kafka Connect to an **Apache Kafka** topic. A **Spring Boot** consumer application reads the Debezium schema envelope and applies corresponding upsert/delete operations to the **Elasticsearch** cluster.

Detailed technical design decisions and requirements are documented in the [Architecture](docs/ARCHITECTURE.md) and [PRD](docs/PRD.md) documents.

## Architecture Decisions & Trade-offs

* **Eventual Consistency over Strong Consistency:** By utilizing Kafka as an asynchronous buffer, the system accepts an eventual consistency model for the Elasticsearch read node. While this prevents the search index from directly impacting the latency of the primary Postgres transactions, the read model may lag slightly behind the source of truth, typically resolving within hundreds of milliseconds under normal load.
* **Complex Schema Evolution:** Utilizing Debezium means structural changes to the PostgreSQL schema (DDL operations) must be carefully coordinated with the consumer application. The Spring consumer expects a specific payload shape, making breaking schema changes high-risk without strict versioning strategies.
* **JVM Garbage Collection Pauses:** The Spring Boot consumer runs on the Java Virtual Machine. While Java 21 generational ZGC offers low latency, intermittent GC pauses could potentially delay message processing from the Kafka partitions, affecting end-to-end sync time during high-throughput burst scenarios.
* **Tombstone Handling Strategy:** Rather than maintaining a complex state machine for deletions, the system utilizes Debezium's explicit `op="d"` (delete) envelope to trigger Elasticsearch document eviction. Null-payload (tombstone) events, typically emitted by Debezium to signal Kafka log compaction, are safely ignored by the consumer to prevent duplicate processing logic.

## Technology Stack

- **Java 21** & **Spring Boot 3.2.4**
- **Apache Kafka** (Debezium distribution 2.5)
- **Debezium PostgreSQL Connector 2.5**
- **Elasticsearch 8.12.0**
- **PostgreSQL 15**
- **Vue 3** & **Vite** (Frontend UI)

## Local Development

The infrastructure stack is orchestrated via Docker Compose.

### 1. Boot the Infrastructure

```bash
cd docker
docker compose up -d
```
*This provisions Zookeeper, Kafka, Postgres (WAL enabled), Kafka Connect, and a single-node Elasticsearch cluster.*

### 2. Register the Debezium Connector

Wait approximately 30-60 seconds for the Kafka Connect REST API to become available (`localhost:8083`), then register the PostgreSQL connector from the host machine:

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @docker/connector-config.json
```
*(Note: If running in a Kubernetes environment or remote host, ensure port 8083 is forwarded or accessible before making this POST request.)*

### 3. Start the Synchronization Daemon

Initialize the Spring Boot consumer application. The daemon interfaces with Kafka on `localhost:9092`, Elasticsearch on `localhost:9200`, and PostgreSQL on `localhost:5433` by default.

```bash
mvn spring-boot:run
```

### 4. Boot the Vue 3 Dashboard

To visualize the end-to-end synchronization out-of-the-box, initialize the Vue frontend:

```bash
cd ui
npm install
npm run dev
```

Navigate to `http://localhost:5173`. Any transactions against the PostgreSQL source are synchronized to the Elasticsearch read-model.

### 5. Alternative CLI Testing

You can also trigger changes manually via `psql`:

```bash
# Connect to Postgres
docker exec -it <postgres-container-id> psql -U postgres -d cdc_demo

# Insert a new order
INSERT INTO orders (customer_id, total_amount, status) VALUES ('CUST-010', 299.99, 'PENDING');
```

Verify via the Elasticsearch API:

```bash
curl http://localhost:9200/orders_index/_search?pretty
```

## Engineering Notes

Proper serialization configurations within Kafka Connect are essential for JVM consumers to cleanly map Postgres types to Elasticsearch entities.

- **Numerics**: Setting `decimal.handling.mode=string` prevents Debezium from wrapping `DECIMAL` types in base64 binary arrays, enabling native JSON mapping.
- **Timestamps**: Setting `time.precision.mode=connect` coerces Postgres microsecond timestamp offsets into standardized epoch milliseconds, cleanly resolving to `java.time.Instant`.

## Testing

Integration configurations utilize Testcontainers to ensure isolated behavior across the consumer's parsing boundaries.

```bash
mvn test
```
