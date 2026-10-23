package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.CartClient;
import com.ecommerce.orderservice.client.CartClient.CartView;
import com.ecommerce.orderservice.client.DownstreamRejectedException;
import com.ecommerce.orderservice.client.DownstreamUnavailableException;
import com.ecommerce.orderservice.client.InventoryClient;
import com.ecommerce.orderservice.client.InventoryClient.ReservationLine;
import com.ecommerce.orderservice.client.InventoryClient.ReservationView;
import com.ecommerce.orderservice.client.ProductClient;
import com.ecommerce.orderservice.client.ProductClient.ProductView;
import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.dto.request.CheckoutRequest;
import com.ecommerce.orderservice.dto.request.ShippingAddressRequest;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.entity.ShippingAddress;
import com.ecommerce.orderservice.exception.BadRequestException;
import com.ecommerce.orderservice.exception.ConflictException;
import com.ecommerce.orderservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Checkout, step by step:
 * <ol>
 *   <li>The order id comes from (user, Idempotency-Key). If that order exists, it is returned (or resumed) - a
 *       retry never creates a second order or reserves stock twice.</li>
 *   <li>Read the cart (customer's own token) - it must be ready for checkout.</li>
 *   <li>Read current prices from product-service (the source of truth) and build the order.</li>
 *   <li>Save it as CREATED, so every stock reservation always belongs to an order row.</li>
 *   <li>Reserve the stock in inventory-service (reference = order number; idempotent there too).</li>
 *   <li>PENDING_PAYMENT (+ ORDER_PLACED event), then empty the cart.</li>
 * </ol>
 * If inventory refuses (not enough stock), the order becomes CHECKOUT_FAILED and the same answer is given on retry.
 * If a service is down, the client gets 503 and can retry with the same key; an order left in CREATED is cleaned
 * up by {@link OrderExpiryJob}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");
    static final String COUNTRY = "IN";

    /** @param created false when this request returned an order placed by an earlier request with the same key */
    public record CheckoutResult(OrderResponse order, boolean created) {
    }

    private final CartClient cartClient;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final CheckoutPersistence persistence;
    private final OrderQueryService queryService;
    private final OrderProperties orderProperties;
    private final Clock clock;

    public CheckoutResult checkout(AuthenticatedUser user, String bearerToken, String idempotencyKey,
                                   CheckoutRequest request) {
        String key = validateKey(idempotencyKey);
        UUID orderId = CustomerOrder.idFor(user.id(), key);

        var existing = queryService.findById(orderId);
        if (existing.isPresent()) {
            return resumeOrReplay(existing.get(), bearerToken);
        }

        CartView cart = cartClient.getCart(bearerToken);
        // A parallel request with the same key may have just placed the order and emptied the cart
        existing = queryService.findById(orderId);
        if (existing.isPresent()) {
            return resumeOrReplay(existing.get(), bearerToken);
        }
        validateCart(cart);
        CustomerOrder order = buildOrder(user, key, cart, request);

        if (request.expectedTotal() != null && request.expectedTotal().compareTo(order.getTotalAmount()) != 0) {
            throw new ConflictException("The order total is now " + order.getTotalAmount() + " " + order.getCurrency()
                    + " (you expected " + request.expectedTotal() + "). Please review your cart and place the order again.");
        }

        try {
            order = persistence.insertNew(order, clock.instant());
        } catch (DataIntegrityViolationException ex) {
            // The same checkout is running in parallel (double click): continue with that order
            CustomerOrder winner = queryService.findById(orderId).orElseThrow(() -> ex);
            return resumeOrReplay(winner, bearerToken);
        }
        return reserveAndPlace(order, bearerToken);
    }

    private CheckoutResult resumeOrReplay(CustomerOrder order, String bearerToken) {
        return switch (order.getStatus()) {
            case CREATED -> reserveAndPlace(order, bearerToken);                    // finish an interrupted checkout
            case CHECKOUT_FAILED -> throw new ConflictException(order.getStatusReason()); // same answer as before
            default -> new CheckoutResult(queryService.toResponse(order.getId(), false), false);
        };
    }

    private CheckoutResult reserveAndPlace(CustomerOrder order, String bearerToken) {
        List<ReservationLine> lines = order.getItems().stream()
                .map(item -> new ReservationLine(item.getProductId(), item.getQuantity()))
                .toList();

        ReservationView reservation;
        try {
            reservation = inventoryClient.reserve(order.getOrderNumber(), lines);
        } catch (DownstreamRejectedException ex) {
            String reason = ex.isConflict() ? ex.getMessage() : "Some products in your cart can't be ordered right now.";
            persistence.markFailed(order.getId(), reason, clock.instant());
            throw new ConflictException(reason);
        }

        if (!reservation.isReserved()) {
            // A reservation with this reference exists but was already released (the checkout timed out)
            String reason = "This checkout has expired. Please place the order again.";
            persistence.markFailed(order.getId(), reason, clock.instant());
            throw new ConflictException(reason);
        }

        boolean placedNow = persistence.markPlaced(order.getId(), clock.instant());
        OrderResponse response = queryService.toResponse(order.getId(), false);
        if (response.status() == OrderStatus.CHECKOUT_FAILED) {
            throw new ConflictException(response.statusReason());
        }
        if (placedNow) {
            cartClient.clearCart(bearerToken);
            log.info("Order {} placed userId={} total={} {}", response.orderNumber(), response.userId(),
                    response.totalAmount(), response.currency());
        }
        return new CheckoutResult(response, placedNow);
    }

    // ---------- Building the order ----------

    private static void validateCart(CartView cart) {
        if (cart.items() == null || cart.items().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }
        if (!cart.pricesUpToDate()) {
            throw new DownstreamUnavailableException("product-service", null);
        }
        if (!cart.checkoutReady()) {
            List<String> problems = cart.items().stream()
                    .filter(line -> !"AVAILABLE".equals(line.status()))
                    .map(line -> line.name() + (line.message() == null ? "" : " (" + line.message() + ")"))
                    .toList();
            throw new ConflictException("Some items in your cart can't be bought right now: "
                    + String.join("; ", problems) + ". Please update your cart.");
        }
    }

    private CustomerOrder buildOrder(AuthenticatedUser user, String key, CartView cart, CheckoutRequest request) {
        Map<UUID, ProductView> products = productClient.findActiveProducts(
                cart.items().stream().map(CartView.Line::productId).toList());

        List<OrderItem> items = new ArrayList<>();
        String currency = null;
        for (CartView.Line line : cart.items()) {
            ProductView product = products.get(line.productId());
            if (product == null) {
                throw new ConflictException(line.name() + " is no longer available. Please remove it from your cart.");
            }
            items.add(new OrderItem(product.id(), product.sku(), product.name(), product.firstImageUrl(),
                    product.sellingPrice(), product.price(), line.quantity()));
            if (currency == null && product.currency() != null) {
                currency = product.currency();
            }
        }

        BigDecimal subtotal = items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return CustomerOrder.create(user.id(), user.email(), key,
                currency == null ? orderProperties.currency() : currency,
                items, orderProperties.shippingFeeFor(subtotal), toAddress(request.shippingAddress()));
    }

    private static ShippingAddress toAddress(ShippingAddressRequest a) {
        return new ShippingAddress(a.fullName().trim(), a.phone(), a.line1().trim(),
                a.line2() == null || a.line2().isBlank() ? null : a.line2().trim(),
                a.city().trim(), a.state().trim(), a.postalCode(), COUNTRY);
    }

    static String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("The Idempotency-Key header is required (a new random value for each order, "
                    + "e.g. a UUID; send the same value again only when retrying the same order)");
        }
        String key = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new BadRequestException("Idempotency-Key must be 8-100 letters, digits, '-' or '_'");
        }
        return key;
    }
}
