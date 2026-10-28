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
import com.ecommerce.cartservice.repository.CartRepository.AddResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cart rules:
 * - Only products that are for sale and in stock can be added, up to app.cart.max-quantity-per-item units each
 *   (or the stock, if lower) and app.cart.max-items different products.
 * - The stored cart only holds quantities and a snapshot; price and stock are fetched live on every view.
 * - Stock is checked but not held: it is reserved only at checkout (order-service, next phase).
 * - Lowering a quantity is always allowed, even for an item that is out of stock or no longer sold.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductCatalogClient catalogClient;
    private final CartProperties cartProperties;
    private final Clock clock;

    // ---------- Read ----------

    @Override
    public CartResponse getCart(UUID userId) {
        List<CartLine> lines = cartRepository.findAll(userId);
        if (lines.isEmpty()) {
            return CartResponse.empty(cartProperties.currency());
        }

        Map<UUID, CatalogProduct> products;
        boolean upToDate;
        try {
            products = catalogClient.findActiveProducts(lines.stream().map(CartLine::productId).toList());
            upToDate = true;
        } catch (CatalogUnavailableException ex) {
            // Still show the cart (from the stored snapshot) rather than failing the whole page
            products = Map.of();
            upToDate = false;
        }
        return buildCart(lines, products, upToDate);
    }

    // ---------- Write ----------

    @Override
    public CartResponse addItem(UUID userId, AddItemRequest request) {
        UUID productId = request.productId();
        int quantityToAdd = request.quantity();
        CatalogProduct product = findProductForSale(productId);

        if (product.availableQuantity() <= 0) {
            throw new ConflictException(product.name() + " is out of stock");
        }

        int alreadyInCart = cartRepository.find(userId, productId).map(CartLine::quantity).orElse(0);
        int perItemLimit = cartProperties.maxQuantityPerItem();
        int maxQuantity = Math.min(perItemLimit, product.availableQuantity());

        CartLine snapshot = new CartLine(productId, product.sku(), product.name(), product.firstImageUrl(),
                product.sellingPrice(), product.currency(), 0, Instant.now(clock));
        AddResult result = cartRepository.add(userId, snapshot, quantityToAdd, maxQuantity, cartProperties.maxItems());

        switch (result.outcome()) {
            case CART_FULL -> throw new BadRequestException(
                    "Your cart can hold at most " + cartProperties.maxItems() + " different products");
            case QUANTITY_LIMIT -> throw quantityLimitError(product, alreadyInCart, perItemLimit);
            case ADDED -> log.info("Cart add userId={} productId={} +{} -> {}", userId, productId, quantityToAdd, result.quantity());
        }
        return getCart(userId);
    }

    @Override
    public CartResponse updateQuantity(UUID userId, UUID productId, UpdateQuantityRequest request) {
        int quantity = request.quantity();
        CartLine line = cartRepository.find(userId, productId)
                .orElseThrow(() -> notInCart(productId));

        if (quantity > cartProperties.maxQuantityPerItem()) {
            throw new BadRequestException("You can have at most " + cartProperties.maxQuantityPerItem()
                    + " units of " + line.name() + " in your cart");
        }

        // Increasing needs the product to be for sale with enough stock; decreasing is always fine
        if (quantity > line.quantity()) {
            CatalogProduct product = catalogClient.findActiveProducts(List.of(productId)).get(productId);
            if (product == null) {
                throw new ConflictException(line.name() + " is no longer available. Please remove it from your cart.");
            }
            if (product.availableQuantity() < quantity) {
                throw new ConflictException(stockMessage(product));
            }
        }

        if (!cartRepository.setQuantity(userId, productId, quantity)) {
            throw notInCart(productId); // removed by another request in the meantime
        }
        log.info("Cart quantity userId={} productId={} {} -> {}", userId, productId, line.quantity(), quantity);
        return getCart(userId);
    }

    @Override
    public CartResponse removeItem(UUID userId, UUID productId) {
        if (cartRepository.remove(userId, productId)) {
            log.info("Cart remove userId={} productId={}", userId, productId);
        }
        return getCart(userId);
    }

    @Override
    public void clear(UUID userId) {
        cartRepository.clear(userId);
        log.info("Cart cleared userId={}", userId);
    }

    // ---------- Building the response ----------

    private CartResponse buildCart(List<CartLine> lines, Map<UUID, CatalogProduct> products, boolean upToDate) {
        List<CartItemResponse> items = lines.stream()
                .sorted(Comparator.comparing(CartLine::addedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(line -> line.productId().toString()))
                .map(line -> toItem(line, products.get(line.productId()), upToDate))
                .toList();

        int totalQuantity = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal savings = BigDecimal.ZERO;
        for (CartItemResponse item : items) {
            totalQuantity += item.quantity();
            if (item.status() == CartItemStatus.AVAILABLE) {
                subtotal = subtotal.add(item.lineTotal());
                if (item.listPrice() != null && item.listPrice().compareTo(item.unitPrice()) > 0) {
                    savings = savings.add(item.listPrice().subtract(item.unitPrice())
                            .multiply(BigDecimal.valueOf(item.quantity())));
                }
            }
        }

        boolean allAvailable = items.stream().allMatch(i -> i.status() == CartItemStatus.AVAILABLE);
        List<String> warnings = new ArrayList<>();
        if (!upToDate) {
            warnings.add("Prices and stock could not be refreshed right now; showing the prices from when items were added.");
        } else if (!allAvailable) {
            warnings.add("Some items can't be bought right now. Update or remove them to check out.");
        }

        String currency = products.values().stream().map(CatalogProduct::currency).filter(c -> c != null && !c.isBlank())
                .findFirst().orElse(cartProperties.currency());

        return new CartResponse(items, totalQuantity, money(subtotal), money(savings), currency,
                upToDate && allAvailable, upToDate, List.copyOf(warnings));
    }

    private CartItemResponse toItem(CartLine line, CatalogProduct product, boolean upToDate) {
        if (!upToDate || product == null) {
            CartItemStatus status = upToDate ? CartItemStatus.UNAVAILABLE : CartItemStatus.UNKNOWN;
            String message = upToDate ? "No longer available" : null;
            BigDecimal price = line.priceWhenAdded();
            return new CartItemResponse(line.productId(), line.sku(), line.name(), line.imageUrl(), line.quantity(),
                    price, null, price, false, lineTotal(price, line.quantity()), null, status, message, line.addedAt());
        }

        BigDecimal price = product.sellingPrice();
        boolean priceChanged = line.priceWhenAdded() != null && price.compareTo(line.priceWhenAdded()) != 0;

        CartItemStatus status;
        String message = null;
        if (product.availableQuantity() <= 0) {
            status = CartItemStatus.OUT_OF_STOCK;
            message = "Out of stock";
        } else if (product.availableQuantity() < line.quantity()) {
            status = CartItemStatus.INSUFFICIENT_STOCK;
            message = "Only " + product.availableQuantity() + " left in stock";
        } else {
            status = CartItemStatus.AVAILABLE;
        }
        if (priceChanged) {
            String priceMessage = (price.compareTo(line.priceWhenAdded()) < 0 ? "Price dropped" : "Price increased")
                    + " from " + money(line.priceWhenAdded()) + " to " + money(price);
            message = message == null ? priceMessage : message + ". " + priceMessage;
        }

        return new CartItemResponse(line.productId(), product.sku(), product.name(), product.firstImageUrl(),
                line.quantity(), price, product.price(), line.priceWhenAdded(), priceChanged,
                lineTotal(price, line.quantity()), product.availableQuantity(), status, message, line.addedAt());
    }

    // ---------- Helpers ----------

    private CatalogProduct findProductForSale(UUID productId) {
        CatalogProduct product = catalogClient.findActiveProducts(List.of(productId)).get(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found or not available for sale: " + productId);
        }
        return product;
    }

    private static RuntimeException quantityLimitError(CatalogProduct product, int alreadyInCart, int perItemLimit) {
        String inCart = alreadyInCart > 0 ? " (you already have " + alreadyInCart + " in your cart)" : "";
        if (product.availableQuantity() < perItemLimit) {
            return new ConflictException(stockMessage(product) + inCart);
        }
        return new BadRequestException("You can have at most " + perItemLimit + " units of " + product.name()
                + " in your cart" + inCart);
    }

    private static String stockMessage(CatalogProduct product) {
        return "Only " + product.availableQuantity() + " units of " + product.name() + " are available";
    }

    private static ResourceNotFoundException notInCart(UUID productId) {
        return new ResourceNotFoundException("Product " + productId + " is not in your cart");
    }

    private static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice == null ? null : money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
