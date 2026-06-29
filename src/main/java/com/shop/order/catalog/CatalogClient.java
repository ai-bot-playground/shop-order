package com.shop.order.catalog;

import java.math.BigDecimal;

/** Looks up a product's unit price (needed to compute the payment amount). */
public interface CatalogClient {

    BigDecimal priceOf(String productId);
}
