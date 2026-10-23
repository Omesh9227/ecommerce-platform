package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.CartClient;
import com.ecommerce.orderservice.client.CartClient.CartView;
import com.ecommerce.orderservice.client.DownstreamRejectedException;
import com.ecommerce.orderservice.client.DownstreamUnavailableException;
import com.ecommerce.orderservice.client.InventoryClient;
import com.ecommerce.orderservice.client.InventoryClient.ReservationView;
import com.ecommerce.orderservice.client.ProductClient;
import com.ecommerce.orderservice.client.ProductClient.ProductView;
import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.dto.request.CheckoutRequest;
import com.ecommerce.orderservice.dto.request.ShippingAddressRequest;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.exception.BadRequestException;
import com.ecommerce.orderservice.exception.ConflictException;
import com.ecommerce.orderservice.security.AuthenticatedUser;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    private static final String TOKEN = "Bearer user-token";
    private static final String KEY = "checkout-key-1";

    @Mock
    private CartClient cartClient;
    @Mock
    private ProductClient productClient;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private CheckoutPersistence persistence;
    @Mock
    private OrderQueryService queryService;

    private CheckoutService checkoutService;

    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "omesh@example.com", "CUSTOMER");
    private final UUID phoneId = UUID.randomUUID();
    private final CheckoutRequest request = new CheckoutRequest(
            new ShippingAddressRequest("Omesh", "9876543210", "Line 1", " ", "Pune", "MH", "411001"), null);

    @BeforeEach
    void setUp() {
        OrderProperties properties = new OrderProperties("INR", new BigDecimal("40"), new BigDecimal("499"),
                Duration.ofMinutes(30), Duration.ofMinutes(2), 30000);
        checkoutService = new CheckoutService(cartClient, productClient, inventoryClient, persistence, queryService,
                properties, Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
    }

    private CartView cart(int quantity, boolean ready) {
        return new CartView(List.of(new CartView.Line(phoneId, "Phone", quantity, ready ? "AVAILABLE" : "OUT_OF_STOCK",
                ready ? null : "Out of stock")), BigDecimal.ZERO, ready, true, List.of());
    }

    private void productExists(String sellingPrice) {
        when(productClient.findActiveProducts(List.of(phoneId))).thenReturn(Map.of(phoneId,
                new ProductView(phoneId, "PHONE", "Phone", new BigDecimal("1000"), new BigDecimal(sellingPrice), "INR", List.of())));
    }

    private static OrderResponse response(OrderStatus status, String reason) {
        return new OrderResponse(UUID.randomUUID(), "ORD-X", UUID.randomUUID(), status, reason, List.of(), 1,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, "INR", null, null, null, null, null, null, null,
                null, null, null, List.of());
    }

    @Test
    void newCheckout_reservesUnderTheOrderNumber_placesIt_andClearsTheCart() {
        when(queryService.findById(any())).thenReturn(Optional.empty());
        when(cartClient.getCart(TOKEN)).thenReturn(cart(2, true));
        productExists("900.00");
        when(persistence.insertNew(any(CustomerOrder.class), any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryClient.reserve(anyString(), anyList())).thenReturn(new ReservationView("ORD-X", "RESERVED", List.of()));
        when(persistence.markPlaced(any(), any())).thenReturn(true);
        when(queryService.toResponse(any(UUID.class), eq(false))).thenReturn(response(OrderStatus.PENDING_PAYMENT, null));

        CheckoutService.CheckoutResult result = checkoutService.checkout(user, TOKEN, KEY, request);

        assertThat(result.created()).isTrue();
        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(persistence).insertNew(saved.capture(), any());
        CustomerOrder order = saved.getValue();
        assertThat(order.getId()).isEqualTo(CustomerOrder.idFor(user.id(), KEY));
        assertThat(order.getSubtotal()).isEqualByComparingTo("1800.00");
        assertThat(order.getShippingAddress().getLine2()).isNull(); // blank becomes null
        verify(inventoryClient).reserve(eq(order.getOrderNumber()), anyList());
        verify(cartClient).clearCart(TOKEN);
    }

    @Test
    void existingOrder_isReturned_withoutCallingOtherServices() {
        CustomerOrder existing = mock(CustomerOrder.class);
        when(existing.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(existing.getId()).thenReturn(UUID.randomUUID());
        when(queryService.findById(CustomerOrder.idFor(user.id(), KEY))).thenReturn(Optional.of(existing));
        when(queryService.toResponse(any(UUID.class), eq(false))).thenReturn(response(OrderStatus.CONFIRMED, null));

        CheckoutService.CheckoutResult result = checkoutService.checkout(user, TOKEN, KEY, request);

        assertThat(result.created()).isFalse();
        verifyNoInteractions(cartClient, productClient, inventoryClient);
    }

    @Test
    void failedCheckout_givesTheSameAnswerAgain() {
        CustomerOrder failed = mock(CustomerOrder.class);
        when(failed.getStatus()).thenReturn(OrderStatus.CHECKOUT_FAILED);
        when(failed.getStatusReason()).thenReturn("Cannot reserve stock. PHONE: requested 2, available 1");
        when(queryService.findById(any())).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> checkoutService.checkout(user, TOKEN, KEY, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cannot reserve stock. PHONE: requested 2, available 1");
        verifyNoInteractions(cartClient, inventoryClient);
    }

    @Test
    void notEnoughStock_marksTheCheckoutFailed() {
        when(queryService.findById(any())).thenReturn(Optional.empty());
        when(cartClient.getCart(TOKEN)).thenReturn(cart(2, true));
        productExists("900.00");
        when(persistence.insertNew(any(CustomerOrder.class), any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryClient.reserve(anyString(), anyList()))
                .thenThrow(new DownstreamRejectedException("inventory-service", 409, "Cannot reserve stock. PHONE: requested 2, available 1"));

        assertThatThrownBy(() -> checkoutService.checkout(user, TOKEN, KEY, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("available 1");
        verify(persistence).markFailed(any(), eq("Cannot reserve stock. PHONE: requested 2, available 1"), any());
        verify(persistence, never()).markPlaced(any(), any());
        verify(cartClient, never()).clearCart(anyString());
    }

    @Test
    void inventoryDown_leavesTheOrderForARetry() {
        when(queryService.findById(any())).thenReturn(Optional.empty());
        when(cartClient.getCart(TOKEN)).thenReturn(cart(1, true));
        productExists("900.00");
        when(persistence.insertNew(any(CustomerOrder.class), any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryClient.reserve(anyString(), anyList())).thenThrow(new DownstreamUnavailableException("inventory-service", null));

        assertThatThrownBy(() -> checkoutService.checkout(user, TOKEN, KEY, request))
                .isInstanceOf(DownstreamUnavailableException.class);
        verify(persistence, never()).markFailed(any(), any(), any());
    }

    @Test
    void releasedReservation_meansTheCheckoutExpired() {
        when(queryService.findById(any())).thenReturn(Optional.empty());
        when(cartClient.getCart(TOKEN)).thenReturn(cart(1, true));
        productExists("900.00");
        when(persistence.insertNew(any(CustomerOrder.class), any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryClient.reserve(anyString(), anyList())).thenReturn(new ReservationView("ORD-X", "RELEASED", List.of()));

        assertThatThrownBy(() -> checkoutService.checkout(user, TOKEN, KEY, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("expired");
        verify(persistence).markFailed(any(), anyString(), any());
    }

    @Test
    void cartNotReady_orEmpty_isRefusedBeforeAnythingIsSaved() {
        when(queryService.findById(any())).thenReturn(Optional.empty());
        when(cartClient.getCart(TOKEN)).thenReturn(cart(1, false));
        assertThatThrownBy(() -> checkoutService.checkout(user, TOKEN, KEY, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Phone (Out of stock)");

        when(cartClient.getCart(TOKEN)).thenReturn(new CartView(List.of(), BigDecimal.ZERO, false, true, List.of()));
        assertThatThrownBy(() -> checkoutService.checkout(user, TOKEN, KEY, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Your cart is empty");

        verifyNoInteractions(persistence, inventoryClient);
    }

    @Test
    void idempotencyKey_isValidated() {
        assertThatThrownBy(() -> CheckoutService.validateKey(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> CheckoutService.validateKey("short")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> CheckoutService.validateKey("no spaces allowed")).isInstanceOf(BadRequestException.class);
        assertThat(CheckoutService.validateKey(" 3fa85f64-5717-4562-b3fc-2c963f66afa6 "))
                .isEqualTo("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    }
}
