package com.shop.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status;

    @Column(name = "payment_deadline")
    private OffsetDateTime paymentDeadline;

    protected Order() {
    }

    public Order(String id, String idempotencyKey, String productId, long quantity, BigDecimal amount, String status) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getPaymentDeadline() {
        return paymentDeadline;
    }

    public void setPaymentDeadline(OffsetDateTime paymentDeadline) {
        this.paymentDeadline = paymentDeadline;
    }
}
