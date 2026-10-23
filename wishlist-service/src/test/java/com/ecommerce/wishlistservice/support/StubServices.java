package com.ecommerce.wishlistservice.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Stands in for product-service (GET /api/v1/products/lookup) and cart-service (POST /api/v1/cart/items) in tests.
 * Like the real lookup it only returns products that are for sale.
 */
public final class StubServices {

    public enum Service { PRODUCT, CART }

    /** What wishlist-service sent to cart-service. */
    public record CartRequest(String authorization, UUID productId, int quantity) {
    }

    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<UUID, Map<String, Object>> products = new ConcurrentHashMap<>();
    private final Map<Service, Boolean> down = new ConcurrentHashMap<>();
    private final List<CartRequest> cartRequests = new CopyOnWriteArrayList<>();
    private volatile int cartRefusalStatus;
    private volatile String cartRefusalMessage;

    private StubServices(HttpServer server) {
        this.server = server;
    }

    public static StubServices start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(8));
            StubServices stub = new StubServices(server);
            server.createContext("/api/v1/products/lookup", stub::handleLookup);
            server.createContext("/api/v1/cart/items", stub::handleCart);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the stub server", e);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void reset() {
        products.clear();
        down.clear();
        cartRequests.clear();
        cartRefusalStatus = 0;
        cartRefusalMessage = null;
    }

    // ---------- Test setup ----------

    /** A product for sale. */
    public UUID product(String sku, String listPrice, String sellingPrice, int availableQuantity) {
        UUID id = UUID.randomUUID();
        Map<String, Object> p = new ConcurrentHashMap<>();
        p.put("id", id.toString());
        p.put("sku", sku);
        p.put("name", "Product " + sku);
        p.put("price", new BigDecimal(listPrice));
        p.put("sellingPrice", new BigDecimal(sellingPrice));
        p.put("currency", "INR");
        p.put("status", "ACTIVE");
        p.put("availableQuantity", availableQuantity);
        p.put("inStock", availableQuantity > 0);
        p.put("imageUrls", List.of("https://cdn.example.com/" + sku.toLowerCase() + ".jpg"));
        products.put(id, p);
        return id;
    }

    public void setSellingPrice(UUID productId, String sellingPrice) {
        products.get(productId).put("sellingPrice", new BigDecimal(sellingPrice));
    }

    public void setStock(UUID productId, int availableQuantity) {
        products.get(productId).put("availableQuantity", availableQuantity);
        products.get(productId).put("inStock", availableQuantity > 0);
    }

    /** No longer for sale: the lookup stops returning it. */
    public void discontinue(UUID productId) {
        products.remove(productId);
    }

    public void setDown(Service service, boolean isDown) {
        down.put(service, isDown);
    }

    /** cart-service answers every add with this error, e.g. 409 "out of stock". */
    public void cartRefuses(int status, String message) {
        this.cartRefusalStatus = status;
        this.cartRefusalMessage = message;
    }

    public List<CartRequest> cartRequests() {
        return cartRequests;
    }

    // ---------- Handlers ----------

    private void handleLookup(HttpExchange exchange) throws IOException {
        if (Boolean.TRUE.equals(down.get(Service.PRODUCT))) {
            respond(exchange, 503, Map.of("status", 503, "message", "down"));
            return;
        }
        String query = exchange.getRequestURI().getRawQuery();
        List<Object> found = new ArrayList<>();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("ids=")) {
                    for (String id : URLDecoder.decode(param.substring(4), StandardCharsets.UTF_8).split(",")) {
                        Map<String, Object> p = products.get(UUID.fromString(id.trim()));
                        if (p != null) {
                            found.add(new LinkedHashMap<>(p));
                        }
                    }
                }
            }
        }
        respond(exchange, 200, Map.of("success", true, "message", "Products fetched successfully", "data", found));
    }

    private void handleCart(HttpExchange exchange) throws IOException {
        JsonNode body = json.readTree(exchange.getRequestBody().readAllBytes());
        cartRequests.add(new CartRequest(exchange.getRequestHeaders().getFirst("Authorization"),
                UUID.fromString(body.get("productId").asText()), body.get("quantity").asInt()));
        if (Boolean.TRUE.equals(down.get(Service.CART))) {
            respond(exchange, 503, Map.of("status", 503, "message", "down"));
            return;
        }
        if (cartRefusalStatus != 0) {
            respond(exchange, cartRefusalStatus, Map.of("status", cartRefusalStatus, "message", cartRefusalMessage));
            return;
        }
        respond(exchange, 200, Map.of("success", true, "message", "Item added to cart", "data", Map.of()));
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
