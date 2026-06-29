package com.shop.order.kafka;

import com.shop.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private final OrderService service;

    public PaymentEventListener(OrderService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${shop.order.payment-events-topic:payment-events}")
    public void onMessage(String value) {
        service.handlePaymentEvent(value);
    }
}
