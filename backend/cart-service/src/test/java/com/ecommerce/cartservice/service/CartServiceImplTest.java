package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.client.CatalogProduct;
import com.ecommerce.cartservice.client.CatalogUnavailableException;
import com.ecommerce.cartservice.client.ProductCatalogClient;
import com.ecommerce.cartservice.config.CartProperties;
import com.ecommerce.cartservice.dto.request.AddItemRequest;
import com.ecommerce.cartservice.dto.request.UpdateQuantityRequest;
import com.ecommerce.cartservice.dto.response.CartItemResponse;
import com.ecommerce.cartservice.dto.response.CartItemStatus;
import com.ecommerce.cartservice.dto.response.CartResponse;
import com.ecommerce.cartservice.exception.BadRequestException;
import com.ecommerce.cartservice.exception.ConflictException;
import com.ecommerce.cartservice.exception.ResourceNotFoundException;
import com.ecommerce.cartservice.model.CartLine;
import com.ecommerce.cartservice.repository.CartRepository;
import com.ecommerce.cartservice.repository.CartRepository.AddOutcome;
import com.ecommerce.cartservice.repository.CartRepository.AddResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductCatalogClient catalogClient;

    private CartServiceImpl cartService;

    private final UUID userId = UUID.randomUUID();
    private final UUID phoneId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartRepository, catalogClient,
                new CartProperties(50, 10, Duration.ofDays(30), "INR"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CatalogProduct product(UUID id, String sku, String listPrice, String sellingPrice, int stock) {
        return new CatalogProduct(id, sku, sku + " name", new BigDecimal(listPrice), new BigDecimal(sellingPrice),
                "INR", "ACTIVE", stock, stock > 0, List.of("https://cdn.example.com/" + sku + ".jpg"));
    }

    private static CartLine line(UUID id, String sku, String priceWhenAdded, int quantity, Instant addedAt) {
        return new CartLine(id, sku, sku + " name", null, new BigDecimal(priceWhenAdded), "INR", quantity, addedAt);
    }

    private static CartItemResponse item(CartResponse cart, UUID productId) {
        return cart.items().stream().filter(i -> i.productId().equals(productId)).findFirst().orElseThrow();
    }

    // ---------- getCart ----------

    @Test
    void emptyCart_doesNotCallTheCatalog() {
        when(cartRepository.findAll(userId)).thenReturn(List.of());

        CartResponse cart = cartService.getCart(userId);

        assertThat(cart.items()).isEmpty();
        assertThat(cart.subtotal()).isEqualByComparingTo("0");
        assertThat(cart.checkoutReady()).isFalse();
        verifyNoInteractions(catalogClient);
    }

    @Test
    void cart_usesLivePrices_newestFirst_withTotalsAndSavings() {
        when(cartRepository.findAll(userId)).thenReturn(List.of(
                line(phoneId, "PHONE", "100.00", 2, NOW.minusSeconds(60)),
                line(caseId, "CASE", "10.00", 3, NOW)));
        when(catalogClient.findActiveProducts(anyList())).thenReturn(Map.of(
                phoneId, product(phoneId, "PHONE", "120.00", "90.00", 50),   // price dropped since added
                caseId, product(caseId, "CASE", "10.00", "10.00", 3)));

        CartResponse cart = cartService.getCart(userId);

        assertThat(cart.items()).extracting(CartItemResponse::productId).containsExactly(caseId, phoneId);
        CartItemResponse phone = item(cart, phoneId);
        assertThat(phone.unitPrice()).isEqualByComparingTo("90.00");
        assertThat(phone.lineTotal()).isEqualByComparingTo("180.00");
        assertThat(phone.priceChanged()).isTrue();
        assertThat(phone.message()).isEqualTo("Price dropped from 100.00 to 90.00");
        assertThat(phone.status()).isEqualTo(CartItemStatus.AVAILABLE);
        assertThat(item(cart, caseId).status()).isEqualTo(CartItemStatus.AVAILABLE); // exactly enough stock

        assertThat(cart.totalQuantity()).isEqualTo(5);
        assertThat(cart.subtotal()).isEqualByComparingTo("210.00");
        assertThat(cart.totalSavings()).isEqualByComparingTo("60.00");
        assertThat(cart.currency()).isEqualTo("INR");
        assertThat(cart.checkoutReady()).isTrue();
        assertThat(cart.pricesUpToDate()).isTrue();
        assertThat(cart.warnings()).isEmpty();
    }

    @Test
    void itemsThatCantBeBought_areFlagged_andLeftOutOfTheSubtotal() {
        UUID goneId = UUID.randomUUID();
        when(cartRepository.findAll(userId)).thenReturn(List.of(
                line(phoneId, "PHONE", "100.00", 5, NOW),
                line(caseId, "CASE", "10.00", 1, NOW.minusSeconds(1)),
                line(goneId, "GONE", "50.00", 1, NOW.minusSeconds(2))));
        when(catalogClient.findActiveProducts(anyList())).thenReturn(Map.of(
                phoneId, product(phoneId, "PHONE", "100.00", "100.00", 2),
                caseId, product(caseId, "CASE", "10.00", "10.00", 0)));

        CartResponse cart = cartService.getCart(userId);

        assertThat(item(cart, phoneId).status()).isEqualTo(CartItemStatus.INSUFFICIENT_STOCK);
        assertThat(item(cart, phoneId).message()).isEqualTo("Only 2 left in stock");
        assertThat(item(cart, caseId).status()).isEqualTo(CartItemStatus.OUT_OF_STOCK);
        CartItemResponse gone = item(cart, goneId);
        assertThat(gone.status()).isEqualTo(CartItemStatus.UNAVAILABLE);
        assertThat(gone.name()).isEqualTo("GONE name"); // from the stored snapshot
        assertThat(gone.availableQuantity()).isNull();

        assertThat(cart.subtotal()).isEqualByComparingTo("0");
        assertThat(cart.checkoutReady()).isFalse();
        assertThat(cart.warnings()).singleElement().asString().contains("can't be bought");
    }

    @Test
    void catalogDown_cartIsStillReturned_fromTheSnapshot() {
        when(cartRepository.findAll(userId)).thenReturn(List.of(line(phoneId, "PHONE", "100.00", 2, NOW)));
        when(catalogClient.findActiveProducts(anyList()))
                .thenThrow(new CatalogUnavailableException("down", null));

        CartResponse cart = cartService.getCart(userId);

        CartItemResponse phone = item(cart, phoneId);
        assertThat(phone.status()).isEqualTo(CartItemStatus.UNKNOWN);
        assertThat(phone.unitPrice()).isEqualByComparingTo("100.00");
        assertThat(phone.lineTotal()).isEqualByComparingTo("200.00");
        assertThat(cart.pricesUpToDate()).isFalse();
        assertThat(cart.checkoutReady()).isFalse();
        assertThat(cart.warnings()).singleElement().asString().contains("could not be refreshed");
    }

    // ---------- addItem ----------

    @Test
    void addItem_storesSnapshot_withLimitCappedByStock() {
        when(catalogClient.findActiveProducts(List.of(phoneId)))
                .thenReturn(Map.of(phoneId, product(phoneId, "PHONE", "120.00", "90.00", 4)));
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.empty());
        when(cartRepository.add(eq(userId), any(CartLine.class), eq(2), eq(4), eq(50)))
                .thenReturn(new AddResult(AddOutcome.ADDED, 2));
        when(cartRepository.findAll(userId)).thenReturn(List.of());

        cartService.addItem(userId, new AddItemRequest(phoneId, 2));

        ArgumentCaptor<CartLine> captor = ArgumentCaptor.forClass(CartLine.class);
        verify(cartRepository).add(eq(userId), captor.capture(), eq(2), eq(4), eq(50));
        CartLine snapshot = captor.getValue();
        assertThat(snapshot.sku()).isEqualTo("PHONE");
        assertThat(snapshot.priceWhenAdded()).isEqualByComparingTo("90.00");
        assertThat(snapshot.imageUrl()).isEqualTo("https://cdn.example.com/PHONE.jpg");
        assertThat(snapshot.addedAt()).isEqualTo(NOW);
    }

    @Test
    void addItem_productNotForSale_is404() {
        when(catalogClient.findActiveProducts(List.of(phoneId))).thenReturn(Map.of());

        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(phoneId, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(cartRepository);
    }

    @Test
    void addItem_outOfStock_is409() {
        when(catalogClient.findActiveProducts(List.of(phoneId)))
                .thenReturn(Map.of(phoneId, product(phoneId, "PHONE", "100.00", "100.00", 0)));

        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(phoneId, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PHONE name is out of stock");
        verifyNoInteractions(cartRepository);
    }

    @Test
    void addItem_overStock_is409_withWhatIsAlreadyInTheCart() {
        when(catalogClient.findActiveProducts(List.of(phoneId)))
                .thenReturn(Map.of(phoneId, product(phoneId, "PHONE", "100.00", "100.00", 3)));
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.of(line(phoneId, "PHONE", "100.00", 2, NOW)));
        when(cartRepository.add(eq(userId), any(CartLine.class), eq(2), eq(3), eq(50)))
                .thenReturn(new AddResult(AddOutcome.QUANTITY_LIMIT, 0));

        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(phoneId, 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only 3 units of PHONE name are available (you already have 2 in your cart)");
    }

    @Test
    void addItem_overPerItemLimit_is400() {
        when(catalogClient.findActiveProducts(List.of(phoneId)))
                .thenReturn(Map.of(phoneId, product(phoneId, "PHONE", "100.00", "100.00", 500)));
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.empty());
        when(cartRepository.add(eq(userId), any(CartLine.class), eq(11), eq(10), eq(50)))
                .thenReturn(new AddResult(AddOutcome.QUANTITY_LIMIT, 0));

        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(phoneId, 11)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You can have at most 10 units of PHONE name in your cart");
    }

    @Test
    void addItem_cartFull_is400() {
        when(catalogClient.findActiveProducts(List.of(phoneId)))
                .thenReturn(Map.of(phoneId, product(phoneId, "PHONE", "100.00", "100.00", 5)));
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.empty());
        when(cartRepository.add(eq(userId), any(CartLine.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(new AddResult(AddOutcome.CART_FULL, 0));

        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(phoneId, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Your cart can hold at most 50 different products");
    }

    @Test
    void addItem_catalogDown_fails_withoutTouchingTheCart() {
        when(catalogClient.findActiveProducts(List.of(phoneId))).thenThrow(new CatalogUnavailableException("down", null));

        assertThatThrownBy(() -> cartService.addItem(userId, new AddItemRequest(phoneId, 1)))
                .isInstanceOf(CatalogUnavailableException.class);
        verifyNoInteractions(cartRepository);
    }

    // ---------- updateQuantity ----------

    @Test
    void updateQuantity_productNotInCart_is404() {
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateQuantity(userId, phoneId, new UpdateQuantityRequest(2)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(catalogClient);
    }

    @Test
    void updateQuantity_overPerItemLimit_is400() {
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.of(line(phoneId, "PHONE", "100.00", 1, NOW)));

        assertThatThrownBy(() -> cartService.updateQuantity(userId, phoneId, new UpdateQuantityRequest(11)))
                .isInstanceOf(BadRequestException.class);
        verify(cartRepository, never()).setQuantity(any(), any(), anyInt());
    }

    @Test
    void updateQuantity_increaseBeyondStock_is409() {
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.of(line(phoneId, "PHONE", "100.00", 1, NOW)));
        when(catalogClient.findActiveProducts(List.of(phoneId)))
                .thenReturn(Map.of(phoneId, product(phoneId, "PHONE", "100.00", "100.00", 2)));

        assertThatThrownBy(() -> cartService.updateQuantity(userId, phoneId, new UpdateQuantityRequest(3)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only 2 units of PHONE name are available");
        verify(cartRepository, never()).setQuantity(any(), any(), anyInt());
    }

    @Test
    void updateQuantity_increaseForProductNoLongerSold_is409() {
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.of(line(phoneId, "PHONE", "100.00", 1, NOW)));
        when(catalogClient.findActiveProducts(List.of(phoneId))).thenReturn(Map.of());

        assertThatThrownBy(() -> cartService.updateQuantity(userId, phoneId, new UpdateQuantityRequest(2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void updateQuantity_decrease_needsNoStockCheck() {
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.of(line(phoneId, "PHONE", "100.00", 5, NOW)));
        when(cartRepository.setQuantity(userId, phoneId, 2)).thenReturn(true);
        when(cartRepository.findAll(userId)).thenReturn(List.of());

        cartService.updateQuantity(userId, phoneId, new UpdateQuantityRequest(2));

        verify(cartRepository).setQuantity(userId, phoneId, 2);
        verifyNoInteractions(catalogClient);
    }

    @Test
    void updateQuantity_itemRemovedMeanwhile_is404() {
        when(cartRepository.find(userId, phoneId)).thenReturn(Optional.of(line(phoneId, "PHONE", "100.00", 5, NOW)));
        when(cartRepository.setQuantity(userId, phoneId, 2)).thenReturn(false);

        assertThatThrownBy(() -> cartService.updateQuantity(userId, phoneId, new UpdateQuantityRequest(2)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- remove / clear ----------

    @Test
    void removeItem_isIdempotent() {
        when(cartRepository.remove(userId, phoneId)).thenReturn(false);
        when(cartRepository.findAll(userId)).thenReturn(List.of());

        assertThat(cartService.removeItem(userId, phoneId).items()).isEmpty();
        verify(cartRepository, times(1)).remove(userId, phoneId);
    }

    @Test
    void clear_deletesTheCart() {
        cartService.clear(userId);

        verify(cartRepository).clear(userId);
    }
}
