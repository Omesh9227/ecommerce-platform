package com.ecommerce.wishlistservice.service;

import com.ecommerce.wishlistservice.config.KafkaTopicsProperties;
import com.ecommerce.wishlistservice.config.WishlistProperties;
import com.ecommerce.wishlistservice.entity.WishlistItem;
import com.ecommerce.wishlistservice.event.EventTypes;
import com.ecommerce.wishlistservice.event.ProductEvent;
import com.ecommerce.wishlistservice.event.WishlistEvent;
import com.ecommerce.wishlistservice.outbox.OutboxWriter;
import com.ecommerce.wishlistservice.repository.ProductPriceRepository;
import com.ecommerce.wishlistservice.repository.WishlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceDropServiceTest {

    private final ProductPriceRepository priceRepository = mock(ProductPriceRepository.class);
    private final WishlistItemRepository itemRepository = mock(WishlistItemRepository.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final Instant now = Instant.parse("2026-10-23T10:00:00Z");
    private PriceDropService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PriceDropService(priceRepository, itemRepository, outboxWriter,
                new KafkaTopicsProperties("product-events", "wishlist-events", 3),
                new WishlistProperties(100, new BigDecimal("5"), "INR"), Clock.fixed(now, ZoneOffset.UTC));
        when(priceRepository.findById(productId)).thenReturn(Optional.empty());
    }

    private WishlistItem saved(String price, String email) {
        return new WishlistItem(userId, email, productId, "PIXEL-8", "Pixel 8", null, new BigDecimal(price), now);
    }

    private ProductEvent priced(String price) {
        return new ProductEvent(UUID.randomUUID(), EventTypes.PRODUCT_UPDATED, productId, "PIXEL-8", "Pixel 8 New",
                "ACTIVE", new BigDecimal(price), new BigDecimal(price), 1);
    }

    @ParameterizedTest(name = "last seen {0}, now {1} -> email: {2}")
    @CsvSource({
            "1000, 950, true",     // exactly 5 %
            "1000, 951, false",    // 4.9 %
            "1000, 500, true",
            "1000, 1000, false",   // same
            "1000, 1200, false",   // dearer
            "999.99, 949.99, true" // 5.00005 %
    })
    void emailOnlyForDropsOfAtLeastFivePercent(String lastSeen, String now, boolean expected) {
        assertThat(service.isWorthAnEmail(new BigDecimal(lastSeen), new BigDecimal(now))).isEqualTo(expected);
    }

    @Test
    void drop_writesAnEventWithTheNewName_andRemembersTheAlertedPrice() {
        WishlistItem item = saved("1800", "omesh@example.com");
        when(itemRepository.findByProductId(productId)).thenReturn(List.of(item));

        assertThat(service.onProductEvent(priced("1500"))).isEqualTo(1);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(outboxWriter).write(eq("wishlist-events"), eq(userId.toString()), eq(EventTypes.WISHLIST_PRICE_DROPPED),
                event.capture());
        WishlistEvent sent = (WishlistEvent) event.getValue();
        assertThat(sent.productName()).isEqualTo("Pixel 8 New");
        assertThat(sent.previousPrice()).isEqualByComparingTo("1800");
        assertThat(sent.newPrice()).isEqualByComparingTo("1500");
        assertThat(sent.occurredAt()).isEqualTo(now);
        assertThat(item.priceLastSeen()).isEqualByComparingTo("1500");
    }

    @Test
    void customerWithoutEmail_isSkipped() {
        when(itemRepository.findByProductId(productId)).thenReturn(List.of(saved("1800", null)));

        assertThat(service.onProductEvent(priced("1000"))).isZero();

        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any());
    }

    @Test
    void eventWithoutIds_isRejected_soItIsDeadLettered() {
        ProductEvent broken = new ProductEvent(null, EventTypes.PRODUCT_UPDATED, null, null, null, "ACTIVE", null, null, 1);

        assertThatThrownBy(() -> service.onProductEvent(broken)).isInstanceOf(IllegalArgumentException.class);
    }
}
