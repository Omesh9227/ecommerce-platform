package com.ecommerce.cartservice.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only access to the product catalog (product-service). */
public interface ProductCatalogClient {

    /**
     * The products among {@code ids} that are currently for sale (ACTIVE), with live price and stock.
     * An id missing from the result means the product doesn't exist or is no longer for sale.
     *
     * @throws CatalogUnavailableException if product-service can't answer
     */
    Map<UUID, CatalogProduct> findActiveProducts(Collection<UUID> ids);
}
