package com.ecommerce.productservice.outbox;

import com.ecommerce.productservice.config.KafkaTopicsProperties;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.event.CategoryEvent;
import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.event.ProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records product events in the outbox (same transaction as the product change).
 * Call after saveAndFlush, so the product's version is final. Key = product id: all events for one product
 * go to the same partition and are consumed in order.
 */
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private final OutboxWriter outboxWriter;
    private final KafkaTopicsProperties topics;

    public void productCreated(Product product) {
        write(EventTypes.PRODUCT_CREATED, product);
    }

    public void productUpdated(Product product) {
        write(EventTypes.PRODUCT_UPDATED, product);
    }

    public void productDiscontinued(Product product) {
        write(EventTypes.PRODUCT_DISCONTINUED, product);
    }

    /** CATEGORY_CREATED / CATEGORY_UPDATED / CATEGORY_DELETED on category-events (key = category id). */
    public void category(String eventType, Category category, long version) {
        outboxWriter.write(topics.categoryEvents(), category.getId().toString(), eventType,
                CategoryEvent.of(eventType, category, version));
    }

    private void write(String eventType, Product product) {
        outboxWriter.write(topics.productEvents(), product.getId().toString(), eventType,
                ProductEvent.of(eventType, product));
    }
}
