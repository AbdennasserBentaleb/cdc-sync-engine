package com.portfolio.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.cdc.entity.OrderEntity;
import com.portfolio.cdc.model.OrderDocument;
import com.portfolio.cdc.repository.OrderJpaRepository;
import com.portfolio.cdc.repository.OrderSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class CdcIT {

    private static final Logger log = LoggerFactory.getLogger(CdcIT.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Injected from application property — keeps the test topic source-of-truth aligned. */
    @Value("${cdc.topic.order}")
    private String cdcOrderTopic;

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
            .withDatabaseName("cdc_demo")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.0"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Only ES needs manual wiring if @ServiceConnection support is older or specific URI is needed.
        // But @ServiceConnection for Elasticsearch was added in Spring Boot 3.2.0.
    }

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderSearchRepository orderSearchRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Truncate both stores before every test to prevent count contamination
     * across test runs. Without this, the {@code assertEquals(threadCount, esCount)}
     * assertion fails non-deterministically when prior test data is present.
     */
    @BeforeEach
    void cleanStores() {
        orderJpaRepository.deleteAll();
        orderSearchRepository.deleteAll();
        log.info("Stores truncated before test run");
    }

    @Test
    void testEnterpriseConcurrentOrderSyncFlow() throws Exception {
        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        log.info("Starting Concurrent Sync Test with {} concurrent writes...", threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 1. Create order in Postgres (write model)
                    OrderEntity order = new OrderEntity();
                    order.setCustomerId("CONCURRENT-CUST-" + index);
                    order.setTotalAmount(new BigDecimal("150.00").add(new BigDecimal(index)));
                    order.setStatus("PROCESSING");
                    OrderEntity savedOrder = orderJpaRepository.save(order);

                    // 2. Send CDC "create" event as JSON (simulating Debezium WAL stream)
                    ObjectNode afterNode = objectMapper.createObjectNode();
                    afterNode.put("id", savedOrder.getId());
                    afterNode.put("customer_id", "CONCURRENT-CUST-" + index);
                    afterNode.put("total_amount", String.valueOf(150.00 + index));
                    afterNode.put("status", "PROCESSING");
                    afterNode.put("created_at", System.currentTimeMillis());

                    ObjectNode createEvent = objectMapper.createObjectNode();
                    createEvent.put("op", "c");
                    createEvent.set("after", afterNode);
                    createEvent.set("before", null);

                    // Use the topic property — not a hardcoded string
                    kafkaTemplate.send(cdcOrderTopic, String.valueOf(savedOrder.getId()), createEvent.toString());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to execute concurrent write at index {}", index, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all writes to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads did not finish in time");
        assertEquals(threadCount, successCount.get(), "Not all writes were successful");

        // 3. Verify eventual consistency in Elasticsearch
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    long esCount = orderSearchRepository.count();
                    log.info("Elasticsearch count check — expected: {}, actual: {}", threadCount, esCount);
                    assertEquals(threadCount, esCount,
                            "All orders should be eventually consistent in Elasticsearch");
                });

        log.info("Concurrent Sync Test PASSED: No race conditions, no data loss.");
    }
}
