# CDC Sync Engine (Enterprise CQRS)

[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-7.5-black.svg)](https://kafka.apache.org/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.12-yellow.svg)](https://www.elastic.co/)

An enterprise-grade, event-driven CQRS data synchronization pipeline. This architecture leverages **Debezium** to tail PostgreSQL's Write-Ahead Log (WAL) and **Spring Kafka** to project changes into **Elasticsearch** for high-performance read models.

## Architectural Highlights

- **Deterministic State Transitions:** We eliminate application-level dual writes by exclusively relying on infrastructure-as-code CDC (Debezium) to stream state changes atomically from the database log.
- **Infrastructure-level Isolation:** Decoupled write and read models ensure that the search index is projected asynchronously, preventing the query side from impacting transaction latency on the write side.
- **Handling Tombstones & Hard Deletes:** Hard deletes in PostgreSQL are captured as "Tombstone" events in Kafka. The sync engine explicitly handles these records by removing the corresponding documents from Elasticsearch, ensuring the read model remains a faithful projection of the system of record.
- **At-Least-Once delivery with Consumer-Side Idempotency:** The pipeline handles potential duplicate events by utilizing the record ID as the document ID in Elasticsearch, making the upsert operations idempotent.
- **Deterministic DLQ Routing:** Engineered `@RetryableTopic` configurations split transient exceptions (exponential backoff) from deterministic mapping errors (instant DLQ routing) to prevent partition blocking and poison pills.

## Production Bottlenecks (Scale Ceiling)

The current implementation is designed for validation and low-volume proof-of-concepts. Moving to a production-grade Staff-level environment would require:

- **Multi-node Kafka & Elasticsearch:** The `docker-compose` stack uses single-node clusters. High availability requires a multi-node Kafka cluster (3+ brokers) and a multi-node Elasticsearch cluster with sharding and replication configured for the expected data volume.
- **PgBouncer:** Direct database connections from the CDC connector can overwhelm the primary database as the number of instances grows. PgBouncer is necessary to manage connection pooling efficiently.
- **Schema Registry Evolution:** While Avro is supported, a formal Schema Registry (e.g., Confluent or Apicurio) would be essential for managing schema evolution and ensuring forward/backward compatibility across the pipeline.

For a deeper dive into the engineering decisions, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Quick Start (Fully Containerized)

This project has been completely Dockerized and isolated to prevent any port collisions with other local systems. 

### 1. Start the Stack

To build the application and spin up the entire infrastructure (PostgreSQL, Zookeeper, Kafka, Connect, Elasticsearch, the App Engine, and the Dashboard), navigate to the `docker` directory and use Docker Compose:

```powershell
cd docker
docker compose up -d --build
```

### 2. Verify End-to-End Sync

Once the Docker containers are healthy, trigger a write to the PostgreSQL database (the app runs on port **8087**).
*Note: If using Windows PowerShell, ensure you use `curl.exe` instead of `curl`.*

```bash
curl.exe -X POST http://localhost:8087/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "CUST-001", "totalAmount": 250.00, "status": "PENDING"}'
```

Verify that the read model (Elasticsearch) has been eventually synchronized:
```bash
curl.exe http://localhost:8087/api/orders/index
```

### 3. Open the Dashboard

Navigate to your fully integrated real-time Dashboard to see the CDC sync happening live:
**http://localhost:8087**

## Observability

A production-ready Grafana dashboard is provided in `observability/grafana-dashboard.json`. Import this into Grafana (http://localhost:3000, credentials `admin`/`admin`) to monitor:
- HikariCP active connections
- Kafka Consumer Lag
- HTTP P99 Latency

## Test Suite

To prove the architectural robustness of this pipeline against race conditions, run the massive multi-threaded integration suite:

```bash
./mvnw clean verify
```
