package com.portfolio.cdc.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.portfolio.cdc.model.OrderDocument;
import com.portfolio.cdc.repository.OrderSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CdcEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CdcEventConsumer.class);

    private final OrderSearchRepository orderSearchRepository;

    public CdcEventConsumer(OrderSearchRepository orderSearchRepository) {
        this.orderSearchRepository = orderSearchRepository;
    }

    @KafkaListener(topics = "${cdc.topic.order}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeCdcEvent(@Payload(required = false) String message) {
        if (message == null) {
            log.info("Received tombstone event (null payload), skipping");
            return;
        }
        log.info("Received CDC event: {}", message);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(message);
            JsonNode payloadNode = rootNode.path("payload");
            if (payloadNode.isMissingNode()) {
                payloadNode = rootNode;
            }

            String op = payloadNode.path("op").asText("");

            if ("d".equals(op)) {
                JsonNode beforeNode = payloadNode.path("before");
                if (!beforeNode.isMissingNode() && beforeNode.has("id")) {
                    Integer id = beforeNode.get("id").asInt();
                    orderSearchRepository.deleteById(id);
                    log.info("Successfully deleted Order ID {} from Elasticsearch", id);
                }
                return;
            }

            JsonNode afterNode = payloadNode.path("after");
            if (afterNode.isMissingNode() || afterNode.isNull()) {
                return;
            }

            OrderDocument orderDocument = new OrderDocument();

            if (afterNode.has("id") && !afterNode.get("id").isNull()) {
                orderDocument.setId(afterNode.get("id").asInt());
            }

            if (afterNode.has("customer_id") && !afterNode.get("customer_id").isNull()) {
                orderDocument.setCustomerId(afterNode.get("customer_id").asText());
            }

            if (afterNode.has("total_amount") && !afterNode.get("total_amount").isNull()) {
                orderDocument.setTotalAmount(new BigDecimal(afterNode.get("total_amount").asText()));
            }

            if (afterNode.has("status") && !afterNode.get("status").isNull()) {
                orderDocument.setStatus(afterNode.get("status").asText());
            }

            if (afterNode.has("created_at") && !afterNode.get("created_at").isNull()) {
                long timestampMillis = afterNode.get("created_at").asLong();
                orderDocument.setCreatedAt(timestampMillis);
            }

            orderSearchRepository.save(orderDocument);
            log.info("Successfully synced Order ID {} to Elasticsearch", orderDocument.getId());

        } catch (Exception e) {
            log.error("Error processing CDC event", e);
        }
    }
}
