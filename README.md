# 🚀 CDC Sync Engine

A Change Data Capture (CDC) synchronization mechanism using **Debezium**, **Apache Kafka**, and **Elasticsearch**.

This project demonstrates how to incrementally synchronize a downstream Elasticsearch read-model from a PostgreSQL source without synchronous dual-writes.

## 🏗 System Architecture

The pipeline utilizes **Debezium** to ingest the Postgres WAL, publishing events to **Kafka**. A **Spring Boot** consumer then applies these changes to **Elasticsearch**.

Detailed technical design: [Architecture Docs](docs/ARCHITECTURE.md) | [PRD](docs/PRD.md)

---

## 🛠 Quick Start (How to Run)

Follow these 4 steps to get the engine running.

### Step 1: Pre-requisites & Build
Ensure you have Docker and Java 21 installed.
```bash
# Windows
.\mvnw.cmd clean install

# Linux / MacOS
./mvnw clean install
```

### Step 2: Start Infrastructure (Docker)
Starts Postgres, Kafka, Elasticsearch, and the Sync App.
```bash
cd docker
docker compose up -d --build
```
> [!IMPORTANT]
> The `connector-setup` container is an automated task that registers the Debezium connector. It will show as **Exited (0)** once successful.

### Step 3: Run the Dashboard
Open a new terminal and start the UI:
```bash
cd ui
npm install
npm run dev
```
- **URL**: [http://localhost:5173/](http://localhost:5173/)

### Step 4: Verify Synchronization
Confirm the data pipeline is active:
```bash
curl http://localhost:8083/connectors
```
**Expected Output**: `["orders-connector"]`

> [!TIP]
> **Manual Registration (Fallback)**: If the command above returns `[]`, manually register the connector:
> ```bash
> curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @docker/connector-config.json
> ```

---

## 📝 Troubleshooting

### "Backend Unreachable" in UI
1. **Check Port 8080**: Ensure no other service is using port 8080.
2. **Rebuild**: Run `docker compose up -d --build app`.

### Data Not Syncing
1. **Check Status**: `curl http://localhost:8083/connectors/orders-connector/status`.
2. **Setup Logs**: `docker logs docker-connector-setup-1`.

---

## 🧪 Engineering Notes

* **Numerics**: Uses `decimal.handling.mode=string` for native JSON mapping.
* **Timestamps**: Uses `time.precision.mode=connect` for standardized epoch millis.
* **Testing**: Run integration tests with Testcontainers via `.\mvnw.cmd test`.
