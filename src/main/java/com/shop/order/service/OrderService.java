package com.shop.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.order.catalog.CatalogClient;
import com.shop.order.domain.Order;
import com.shop.order.domain.OutboxEvent;
import com.shop.order.domain.ProcessedEvent;
import com.shop.order.repo.OrderRepository;
import com.shop.order.repo.OutboxRepository;
import com.shop.order.repo.ProcessedEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Saga orchestrator. Drives the order state machine and compensates on failure.
 * All emitted events go through the outbox; consumed events are deduplicated.
 */
@Service
public class OrderService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ProcessedEventRepository processed;
    private final CatalogClient catalog;
    private final String orderTopic;
    private final String paymentTopic;
    private final long paymentTimeoutSeconds;

    public OrderService(OrderRepository orders,
                        OutboxRepository outbox,
                        ProcessedEventRepository processed,
                        CatalogClient catalog,
                        @Value("${shop.order.order-events-topic:order-events}") String orderTopic,
                        @Value("${shop.order.payment-events-topic:payment-events}") String paymentTopic,
                        @Value("${shop.order.payment-timeout-seconds:30}") long paymentTimeoutSeconds) {
        this.orders = orders;
        this.outbox = outbox;
        this.processed = processed;
        this.catalog = catalog;
        this.orderTopic = orderTopic;
        this.paymentTopic = paymentTopic;
        this.paymentTimeoutSeconds = paymentTimeoutSeconds;
    }

    @Transactional
    public Order createOrder(String idempotencyKey, String productId, long quantity, String userToken) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        BigDecimal price = catalog.priceOf(productId, userToken);
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        String orderId = UUID.randomUUID().toString();
        Order order = new Order(orderId, idempotencyKey, productId, quantity, amount, "PENDING");
        orders.save(order);
        emit(orderTopic, "OrderCreated", orderId,
                Map.of("orderId", orderId, "productId", productId, "quantity", quantity, "amount", amount));
        return order;
    }

    @Transactional
    public void handleInventoryEvent(String json) {
        JsonNode e = parse(json);
        String eventId = e.path("eventId").asText();
        if (eventId.isEmpty() || processed.existsById(eventId)) {
            return;
        }
        String type = e.path("type").asText();
        String orderId = e.path("orderId").asText();
        Order order = orders.findById(orderId).orElse(null);
        if (order != null) {
            if ("StockReserved".equals(type) && "PENDING".equals(order.getStatus())) {
                order.setStatus("RESERVED");
                order.setPaymentDeadline(OffsetDateTime.now().plusSeconds(paymentTimeoutSeconds));
                emit(paymentTopic, "PaymentRequested", orderId,
                        Map.of("orderId", orderId, "amount", order.getAmount()));
            } else if ("StockReservationFailed".equals(type) && "PENDING".equals(order.getStatus())) {
                order.setStatus("REJECTED");
                emit(orderTopic, "OrderRejected", orderId, Map.of("orderId", orderId));
            }
        }
        processed.save(new ProcessedEvent(eventId));
    }

    @Transactional
    public void handlePaymentEvent(String json) {
        JsonNode e = parse(json);
        String eventId = e.path("eventId").asText();
        if (eventId.isEmpty() || processed.existsById(eventId)) {
            return;
        }
        String type = e.path("type").asText();
        String orderId = e.path("orderId").asText();
        Order order = orders.findById(orderId).orElse(null);
        if (order != null && "RESERVED".equals(order.getStatus())) {
            if ("PaymentCompleted".equals(type)) {
                order.setStatus("CONFIRMED");
                emit(orderTopic, "OrderConfirmed", orderId, Map.of("orderId", orderId));
            } else if ("PaymentFailed".equals(type)) {
                cancelAndCompensate(order);
            }
        }
        processed.save(new ProcessedEvent(eventId));
    }

    @Transactional
    public void cancelTimedOutSagas() {
        for (Order order : orders.findByStatusAndPaymentDeadlineBefore("RESERVED", OffsetDateTime.now())) {
            cancelAndCompensate(order);
        }
    }

    private void cancelAndCompensate(Order order) {
        order.setStatus("CANCELLED");
        emit(orderTopic, "ReleaseStock", order.getId(),
                Map.of("orderId", order.getId(), "productId", order.getProductId(), "quantity", order.getQuantity()));
        emit(orderTopic, "OrderCancelled", order.getId(), Map.of("orderId", order.getId()));
    }

    private void emit(String topic, String type, String orderId, Map<String, Object> fields) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("type", type);
        body.putAll(fields);
        outbox.save(OutboxEvent.create(eventId, topic, type, orderId, toJson(body)));
    }

    private JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid event JSON", ex);
        }
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
