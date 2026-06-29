package com.shop.order.kafka;

import com.shop.order.domain.OutboxEvent;
import com.shop.order.repo.OutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Publishes outbox rows to their target topic (order-events or payment-events).
 * Disabled in component tests where the outbox is asserted directly.
 */
@Component
@ConditionalOnProperty(name = "shop.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${shop.outbox.poll-ms:1000}")
    @Transactional
    public void publish() {
        List<OutboxEvent> batch = outbox.findTop100ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxEvent e : batch) {
            kafka.send(e.getTopic(), e.getMsgKey(), e.getPayload());
            e.markPublished();
        }
    }
}
