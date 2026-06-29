package com.shop.order.kafka;

import com.shop.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {

    private final OrderService service;

    public InventoryEventListener(OrderService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${shop.order.inventory-events-topic:inventory-events}")
    public void onMessage(String value) {
        service.handleInventoryEvent(value);
    }
}
