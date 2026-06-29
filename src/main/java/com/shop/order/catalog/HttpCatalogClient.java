package com.shop.order.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class HttpCatalogClient implements CatalogClient {

    private final RestClient rest;

    public HttpCatalogClient(@Value("${shop.order.catalog-uri:http://shop-catalog:8080}") String catalogUri) {
        this.rest = RestClient.builder().baseUrl(catalogUri).build();
    }

    @Override
    public BigDecimal priceOf(String productId) {
        // Deserialize into a Map rather than a Jackson tree type: the Boot 4
        // RestClient uses Jackson 3 and cannot construct a Jackson 2 JsonNode,
        // and a Map keeps us independent of either Jackson version.
        Map<?, ?> body = rest.get().uri("/products/{id}", productId).retrieve().body(Map.class);
        Object price = (body == null) ? null : body.get("price");
        if (price == null) {
            throw new IllegalStateException("No price for product " + productId);
        }
        return new BigDecimal(price.toString());
    }
}
