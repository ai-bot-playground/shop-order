package com.shop.order.api;

public record CreateOrderRequest(String productId, long quantity) {
}
