package com.shop.order;

import com.shop.order.catalog.CatalogClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;

/** Replaces the real catalog HTTP client with a fixed price in component tests. */
@TestConfiguration
public class OrderTestStubs {

    @Bean
    @Primary
    public CatalogClient stubCatalogClient() {
        return productId -> new BigDecimal("10.00");
    }
}
