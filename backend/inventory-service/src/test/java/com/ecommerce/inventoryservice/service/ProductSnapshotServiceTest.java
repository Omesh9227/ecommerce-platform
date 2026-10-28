package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.config.InventoryProperties;
import com.ecommerce.inventoryservice.event.EventTypes;
import com.ecommerce.inventoryservice.event.ProductEvent;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotServiceTest {

    @Mock
    private InventoryItemRepository itemRepository;

    private ProductSnapshotService service;
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductSnapshotService(itemRepository, new InventoryProperties(5));
    }

    private ProductEvent event(String type, String sku, String name, String status, long version) {
        return new ProductEvent(UUID.randomUUID(), type, Instant.now(), productId, sku, name, status, version);
    }

    @Test
    void productCreated_upsertsWithDefaultThreshold() {
        service.apply(event(EventTypes.PRODUCT_CREATED, "PIXEL-8", "Pixel 8", "ACTIVE", 0));

        verify(itemRepository).upsertProductSnapshot(productId, "PIXEL-8", "Pixel 8", "ACTIVE", 0L, 5);
    }

    @Test
    void productDiscontinued_upsertsNewStatus() {
        service.apply(event(EventTypes.PRODUCT_DISCONTINUED, "PIXEL-8", "Pixel 8", "DISCONTINUED", 4));

        verify(itemRepository).upsertProductSnapshot(productId, "PIXEL-8", "Pixel 8", "DISCONTINUED", 4L, 5);
    }

    @Test
    void unknownEventType_isIgnored() {
        service.apply(event("PRODUCT_PRICE_CHANGED", "PIXEL-8", "Pixel 8", "ACTIVE", 1));

        verifyNoInteractions(itemRepository);
    }

    @Test
    void missingFields_areRejected_soTheEventGoesToTheDeadLetterTopic() {
        assertThatThrownBy(() -> service.apply(event(EventTypes.PRODUCT_CREATED, " ", "Pixel 8", "ACTIVE", 0)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(itemRepository);
    }
}
