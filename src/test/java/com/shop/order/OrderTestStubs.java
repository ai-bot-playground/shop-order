package com.shop.order;

import com.shop.order.catalog.CatalogClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Replaces the real catalog HTTP client with a fixed price in component tests. */
@TestConfiguration
public class OrderTestStubs {

    @Bean
    @Primary
    public CatalogClient stubCatalogClient() {
        return (productId, userId) -> {
            BigDecimal basePrice = new BigDecimal("10.00");
            if ("user1".equals(userId)) {
                return basePrice.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);
            }
            if ("user2".equals(userId)) {
                return basePrice.multiply(new BigDecimal("1.50")).setScale(2, RoundingMode.HALF_UP);
            }
            return basePrice;
        };
    }
}
