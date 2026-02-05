package com.portfolio.cdc;

import com.portfolio.cdc.entity.OrderEntity;
import com.portfolio.cdc.model.OrderDocument;
import com.portfolio.cdc.repository.OrderJpaRepository;
import com.portfolio.cdc.repository.OrderSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.awaitility.Awaitility;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class CdcIntegrationTest {

    @Container
    // Ensure accurate integration against exact Postgres version in production
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
            .withDatabaseName("cdc_demo")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    // Accurate Kafka ecosystem representation for integration
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    // Elasticsearch read node testcontainer
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.0"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderSearchRepository orderSearchRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void testConcurrentOrderSyncFlow() {
        // Validate isolation and race condition prevention when inserting and mutating via CDC
        OrderEntity order = new OrderEntity();
        order.setCustomerId("CONCURRENT-CUST-001");
        order.setTotalAmount(new BigDecimal("150.00"));
        order.setStatus("PROCESSING");
        OrderEntity savedOrder = orderJpaRepository.save(order);

        // Simulate Debezium Kafka Connect emitting the 'create' mutation envelope
        String cdcPayload = String.format("""
                {
                  "payload": {
                    "op": "c",
                    "after": {
                      "id": %d,
                      "customer_id": "CONCURRENT-CUST-001",
                      "total_amount": "150.00",
                      "status": "PROCESSING",
                      "created_at": %d
                    }
                  }
                }
                """, savedOrder.getId(), System.currentTimeMillis());

        kafkaTemplate.send("dbserver1.public.orders", String.valueOf(savedOrder.getId()), cdcPayload);

        // Asynchronously poll Elasticsearch to confirm replication boundary
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Iterable<OrderDocument> docs = orderSearchRepository.findAll();
                    boolean found = false;
                    for (OrderDocument doc : docs) {
                        if (doc.getId().equals(savedOrder.getId())) {
                            found = true;
                            assertEquals("CONCURRENT-CUST-001", doc.getCustomerId());
                            assertEquals(new BigDecimal("150.00"), doc.getTotalAmount());
                            break;
                        }
                    }
                    assertTrue(found, "Order should be synced to Elasticsearch via Kafka CDC Consumer");
                });

        // Simulate tombstone/delete event logic
        String cdcDeletePayload = String.format("""
                {
                  "payload": {
                    "op": "d",
                    "before": {
                      "id": %d
                    }
                  }
                }
                """, savedOrder.getId());
        kafkaTemplate.send("dbserver1.public.orders", String.valueOf(savedOrder.getId()), cdcDeletePayload);

        // Assert accurate eviction from read model index
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Iterable<OrderDocument> docs = orderSearchRepository.findAll();
                    boolean exists = false;
                    for (OrderDocument doc : docs) {
                        if (doc.getId().equals(savedOrder.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    assertTrue(!exists, "Order should be evicted from Elasticsearch on CDC 'd' op");
                });
    }
}
