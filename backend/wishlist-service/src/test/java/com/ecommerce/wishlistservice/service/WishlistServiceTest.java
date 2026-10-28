package com.ecommerce.wishlistservice.service;

import com.ecommerce.wishlistservice.client.CartClient;
import com.ecommerce.wishlistservice.client.CatalogProduct;
import com.ecommerce.wishlistservice.client.ProductCatalogClient;
import com.ecommerce.wishlistservice.config.WishlistProperties;
import com.ecommerce.wishlistservice.dto.response.WishlistItemResponse;
import com.ecommerce.wishlistservice.dto.response.WishlistItemStatus;
import com.ecommerce.wishlistservice.entity.WishlistItem;
import com.ecommerce.wishlistservice.exception.BadRequestException;
import com.ecommerce.wishlistservice.repository.WishlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WishlistServiceTest {

    private final WishlistItemRepository repository = mock(WishlistItemRepository.class);
    private final ProductCatalogClient catalog = mock(ProductCatalogClient.class);
    private final Instant now = Instant.parse("2026-10-23T10:00:00Z");
    private WishlistService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WishlistService(repository, mock(WishlistStore.class), catalog, mock(CartClient.class),
                new WishlistProperties(100, new BigDecimal("5"), "INR"), Clock.fixed(now, ZoneOffset.UTC));
    }

    private void savedAt(String price) {
        WishlistItem item = new WishlistItem(userId, "omesh@example.com", productId, "PIXEL-8", "Pixel 8", null,
                new BigDecimal(price), now);
        when(repository.findByUserIdOrderByAddedAtDesc(userId)).thenReturn(List.of(item));
    }

    private void catalogHas(String sellingPrice, int stock) {
        CatalogProduct product = new CatalogProduct(productId, "PIXEL-8", "Pixel 8", new BigDecimal("2000"),
                new BigDecimal(sellingPrice), "INR", "ACTIVE", stock, stock > 0, List.of("https://cdn.example.com/p.jpg"));
        when(catalog.findActiveProducts(any())).thenReturn(Map.of(productId, product));
    }

    private WishlistItemResponse onlyItem() {
        return service.getWishlist(userId).items().get(0);
    }

    @Test
    void outOfStock_andCheaper_bothAreMentioned() {
        savedAt("1800");
        catalogHas("1620.5", 0);

        WishlistItemResponse item = onlyItem();

        assertThat(item.status()).isEqualTo(WishlistItemStatus.OUT_OF_STOCK);
        assertThat(item.price()).isEqualByComparingTo("1620.50");
        assertThat(item.price().scale()).isEqualTo(2);
        assertThat(item.priceDifference()).isEqualByComparingTo("-179.50");
        assertThat(item.priceDropped()).isTrue();
        assertThat(item.message()).isEqualTo("Out of stock. Price dropped from 1800.00 to 1620.50");
        assertThat(item.imageUrl()).isEqualTo("https://cdn.example.com/p.jpg");
    }

    @Test
    void samePrice_inStock_hasNoMessage() {
        savedAt("1800");
        catalogHas("1800", 3);

        WishlistItemResponse item = onlyItem();

        assertThat(item.status()).isEqualTo(WishlistItemStatus.AVAILABLE);
        assertThat(item.priceDifference()).isEqualByComparingTo("0");
        assertThat(item.priceDropped()).isFalse();
        assertThat(item.message()).isNull();
    }

    @Test
    void emptyWishlist_doesNotCallTheCatalog() {
        when(repository.findByUserIdOrderByAddedAtDesc(userId)).thenReturn(List.of());

        assertThat(service.getWishlist(userId).totalItems()).isZero();

        verifyNoInteractions(catalog);
    }

    @Test
    void contains_acceptsAtMost100Ids() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ids.add(UUID.randomUUID());
        }

        assertThatThrownBy(() -> service.contains(userId, ids)).isInstanceOf(BadRequestException.class);
        assertThat(service.contains(userId, List.of()).productIds()).isEmpty();
    }
}
