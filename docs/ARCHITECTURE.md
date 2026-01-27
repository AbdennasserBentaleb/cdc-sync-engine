# System Architecture

## Overview

The core ideology of this synchronization engine is simple: the PostgreSQL write-ahead log (WAL) acts as the immutable source of truth, and all downstream data stores are purely derived state. 

By avoiding application-level dual writes, this architecture guarantees eventual consistency between the primary database and the search index (Elasticsearch) without introducing synchronous coupling, transaction-spanning latency, or sprawling retry logic into the primary application.

## Component Breakdown

### PostgreSQL (Source of Truth)

The primary database runs with `wal_level=logical` enabled, allowing Postgres to emit a stream of logical decoding events (via the `pgoutput` plugin). This captures the granular before-and-after state of every committed transaction.

The schema for this implementation is a single `orders` table:

```sql
CREATE TABLE orders (
    id          SERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status      VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Debezium & Kafka Connect

Debezium runs as a Kafka Connect plugin directly monitoring the replication slot on Postgres. It converts WAL entries into self-describing JSON events and publishes them to the Kafka topic `dbserver1.public.orders`.

Every event carries a detailed payload envelope containing:
* `before`: The state of the row prior to the mutation (required for Delete propagation).
* `after`: The state of the row post-mutation (required for Creates/Updates).
* `op`: The explicit operation flag (`c` for create, `u` for update, `d` for delete, `r` for snapshot read).

Two critical connector configurations ensure correct type mapping to the JVM:
* `decimal.handling.mode=string`: Prevents base64 binary encoding of `NUMERIC` types, allowing for out-of-the-box JSON decimal parsing.
* `time.precision.mode=connect`: Unifies Postgres timestamps into epoch milliseconds, eliminating complex microsecond precision conversions in the Java consumer.

### Apache Kafka (Event Backbone)

Kafka acts as the durable, replayable buffer. The consumer group (`cdc-sync-group`) subscribes to the topic. If the consumer service experiences downtime, events buffer harmlessly in the partition. Upon recovery, the consumer resumes from its last committed offset, enforcing complete eventual consistency.

### Spring Boot Sync Engine 

The consumer application (`cdc-sync-engine`) receives the stringified JSON payload from Kafka. 

The CDC processing pipeline executes as follows in `CdcEventConsumer.java`:
1. Parse the inbound Debezium envelope into a `JsonNode`.
2. Extract the `op` flag.
3. If `op` == `d` (Delete): Extract the `id` from the `before` node, and invoke `OrderSearchRepository.deleteById(id)`.
4. If `op` == `c` or `u` (Create/Update): Extract the `after` node, deserialize the fields into an `OrderDocument` instance, and invoke `OrderSearchRepository.save(orderDocument)`.

Malformed envelopes and schema-less tombstone events are logged and cleanly bypassed to prevent partition halting.

### Elasticsearch (Read Model)

Elasticsearch acts as the highly optimized read-model. The `orders_index` mirrors the Postgres table but provides the specialized inverted index required for aggregations and arbitrary search queries. Spring Data Elasticsearch automatically provisions the index based on the `OrderDocument` mappings.

### Vue 3 Demonstration Dashboard

To visually assert the capabilities of the CDC pipeline, a standalone Single Page Application (SPA) is built with Vue 3 and Vite (located in `/ui`).

**Note on Demonstration Coupling**: To power the UI, the Spring Boot application exposes a `DemoController` providing basic REST CRUD operations against Postgres, alongside read-only operations against Elasticsearch. **In a production microservice topology, the application executing the Postgres writes would be completely alienated from the downstream CDC consumer running the synchronization.**

## Data Flow Diagram

```text
       [ Vue 3 Dashboard ]
         |           ^
      (Writes)    (Reads)
         v           |
   [ Postgres ]  [ Elasticsearch ]
         |           ^
      [ WAL ]        |
         v           |
   [ Debezium ]      | (Saves/Deletes)
         |           |
     [ Kafka ]       |
         v           |
  [ Spring Boot Sync Engine ]
```

## Production Considerations

Scaling this architecture for production requires several hardening steps:
- **Broker Management**: Transition from Zookeeper/Kafka to a managed schema registry and broker (e.g., MSK or Confluent).
- **Dead Letter Queue (DLQ)**: Implement a DLQ in the Spring Kafka configuration to safely route unparseable payloads built by schema drift, ensuring the primary partition never stalls.
- **Consumer Concurrency**: Tune the `@KafkaListener` concurrency strictly in tandem with the topic partition count to scale throughput horizontally.
- **Offset Management**: Transition Kafka Connect offset storage to an isolated external cluster to avoid coupling connector state with the transmission cluster.
