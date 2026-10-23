package com.ecommerce.inventoryservice.outbox;

import com.ecommerce.inventoryservice.config.KafkaTopicsProperties;
import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.event.EventTypes;
import com.ecommerce.inventoryservice.event.InventoryEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Records inventory events in the outbox. Call after the item change has been flushed (so its version is final). */
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    private final OutboxWriter outboxWriter;
    private final KafkaTopicsProperties topics;

    public void inventoryUpdated(InventoryItem item) {
        write(EventTypes.INVENTORY_UPDATED, item);
    }

    public void inventoryLow(InventoryItem item) {
        write(EventTypes.INVENTORY_LOW, item);
    }

    private void write(String eventType, InventoryItem item) {
        outboxWriter.write(topics.inventoryEvents(), item.getProductId().toString(), eventType,
                InventoryEvent.of(eventType, item));
    }
}
