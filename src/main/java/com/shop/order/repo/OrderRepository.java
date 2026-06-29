package com.shop.order.repo;

import com.shop.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByStatusAndPaymentDeadlineBefore(String status, OffsetDateTime deadline);
}
