package com.shop.order.api;

import com.shop.order.domain.Order;
import com.shop.order.repo.OrderRepository;
import com.shop.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;
    private final OrderRepository orders;

    public OrderController(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Session", required = false) String userSession,
            @RequestBody CreateOrderRequest req) {
        Order order = service.createOrder(idempotencyKey, req.productId(), req.quantity(), userSession);
        return ResponseEntity.accepted().body(Map.of("orderId", order.getId(), "status", order.getStatus()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return orders.findById(id)
                .<ResponseEntity<?>>map(o -> ResponseEntity.ok(Map.of("orderId", o.getId(), "status", o.getStatus())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
