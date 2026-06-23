package com.shop.order.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class HttpCatalogClient implements CatalogClient {

    private final RestClient rest;

    public HttpCatalogClient(@Value("${shop.order.catalog-uri:http://shop-catalog:8080}") String catalogUri) {
        this.rest = RestClient.builder().baseUrl(catalogUri).build();
    }

    @Override
    public BigDecimal priceOf(String productId) {
        JsonNode body = rest.get().uri("/products/{id}", productId).retrieve().body(JsonNode.class);
        if (body == null || body.path("price").isMissingNode()) {
            throw new IllegalStateException("No price for product " + productId);
        }
        return body.path("price").decimalValue();
    }
}
