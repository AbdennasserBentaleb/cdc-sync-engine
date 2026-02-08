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
docker compose up -d --build
```
*This automatically provisions Zookeeper, Kafka, Postgres (WAL enabled), Kafka Connect, Elasticsearch, the CDC Sync Engine (`app`), and safely registers the Debezium connector in the background.*

You can view the logs of the sync engine via:
```bash
docker compose logs -f app
```

### 2. Boot the Vue 3 Dashboard

To visualize the end-to-end synchronization out-of-the-box, initialize the Vue frontend:

```bash
cd ../ui
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

## API Documentation

The Spring Boot application exposes testing endpoints to easily interact with the pipeline:

- `GET /api/orders/source` - Fetch all orders directly from the PostgreSQL source.
- `GET /api/orders/index` - Fetch all synchronized documents from the Elasticsearch read-model.
- `POST /api/orders` - Create a new order in PostgreSQL (triggers Debezium CDC sync). 
  - *Example payload:* `{"customerId": "CUST-001", "totalAmount": 150.00, "status": "PENDING"}`
- `PUT /api/orders/{id}` - Update an existing order.
- `DELETE /api/orders/{id}` - Delete an order and propagate the tombstone event.

## Engineering Notes

Proper serialization configurations within Kafka Connect are essential for JVM consumers to cleanly map Postgres types to Elasticsearch entities.

- **Numerics**: Setting `decimal.handling.mode=string` prevents Debezium from wrapping `DECIMAL` types in base64 binary arrays, enabling native JSON mapping.
- **Timestamps**: Setting `time.precision.mode=connect` coerces Postgres microsecond timestamp offsets into standardized epoch milliseconds, cleanly resolving to `java.time.Instant`.

## Testing

Integration configurations utilize Testcontainers to ensure isolated behavior across the consumer's parsing boundaries.

```bash
mvn test
```
