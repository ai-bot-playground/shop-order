package com.shop.order.catalog;

import java.math.BigDecimal;

/** Looks up a product's unit price (needed to compute the payment amount).
 *  The session token is forwarded to shop-catalog so that the per-user margin
 *  can be applied to the base price before it is returned. */
public interface CatalogClient {

    BigDecimal priceOf(String productId, String sessionToken);
}
