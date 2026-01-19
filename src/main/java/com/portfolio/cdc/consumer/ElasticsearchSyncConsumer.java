package com.portfolio.cdc.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.cdc.model.OrderDocument;
import com.portfolio.cdc.repository.OrderSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

@Service
public class ElasticsearchSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSyncConsumer.class);

    private final OrderSearchRepository orderSearchRepository;
    private final ObjectMapper objectMapper;

    public ElasticsearchSyncConsumer(OrderSearchRepository orderSearchRepository, ObjectMapper objectMapper) {
        this.orderSearchRepository = orderSearchRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Enterprise-Grade @RetryableTopic configuration:
     * - Exponential backoff for transient exceptions.
     * - Fast-fail (exclude) for deterministic exceptions (Jackson parsing, illegal arguments).
     * - dltTopicSuffix explicitly set to ".DLQ"
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            dltTopicSuffix = ".DLQ",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            include = {
                    SocketTimeoutException.class,
                    DataAccessResourceFailureException.class, // Spring Data wrapper for ES 503/connection errors
                    java.io.IOException.class
            }
    )
    @KafkaListener(topics = "${cdc.topic.order}", groupId = "es-sync-group")
    public void consumeCdcEvent(@Payload String eventString,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        
        log.debug("Consuming message from topic {} offset {}", topic, offset);

        if (eventString == null || eventString.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty CDC payload received");
        }

        JsonNode event = objectMapper.readTree(eventString);
        String op = event.path("op").asText("");

        // Handle Delete Operation
        if ("d".equals(op)) {
            JsonNode before = event.get("before");
            if (before != null && before.hasNonNull("id")) {
                orderSearchRepository.deleteById(before.get("id").asInt());
                log.info("Deleted order {} from Elasticsearch index", before.get("id").asInt());
            }
            return;
        }

        // Handle Upsert Operations
        JsonNode after = event.get("after");
        if (after == null || after.isNull()) {
            throw new IllegalArgumentException("CDC event missing 'after' payload for upsert operation");
        }

        OrderDocument orderDocument = mapToDocument(after);
        orderSearchRepository.save(orderDocument);
        log.info("Successfully synced order {} to Elasticsearch", orderDocument.getId());
    }

    @DltHandler
    public void handleDlt(@Payload String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage,
                          @Header(value = KafkaHeaders.EXCEPTION_CAUSE_FQCN, required = false) String cause) {
        log.error("POISON PILL DETECTED. Instantly routed to DLQ topic: {}. Cause: {}. Error: {}. Payload: {}",
                topic, cause, errorMessage, payload);
    }


    private OrderDocument mapToDocument(JsonNode after) {
        OrderDocument orderDocument = new OrderDocument();
        if (after.hasNonNull("id"))           orderDocument.setId(after.get("id").asInt());
        if (after.hasNonNull("customer_id"))  orderDocument.setCustomerId(after.get("customer_id").asText());
        if (after.hasNonNull("total_amount")) orderDocument.setTotalAmount(new BigDecimal(after.get("total_amount").asText()));
        if (after.hasNonNull("status"))       orderDocument.setStatus(after.get("status").asText());
        if (after.hasNonNull("created_at"))   orderDocument.setCreatedAt(after.get("created_at").asLong());
        return orderDocument;
    }
}
