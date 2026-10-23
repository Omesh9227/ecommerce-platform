package com.ecommerce.orderservice.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for cart-service, product-service and inventory-service in tests - one small HTTP server.
 * Inventory behaves like the real one: reservations are all-or-nothing, idempotent by order reference, and
 * release / confirm change the stock.
 */
public final class StubServices {

    public enum Service { CART, PRODUCT, INVENTORY }

    public static final class Stock {
        public int onHand;
        public int reserved;

        Stock(int onHand) {
            this.onHand = onHand;
        }

        public int available() {
            return onHand - reserved;
        }
    }

    public static final class Reservation {
        public final String reference;
        public final Map<UUID, Integer> items;
        public volatile String status = "RESERVED";

        Reservation(String reference, Map<UUID, Integer> items) {
            this.reference = reference;
            this.items = items;
        }
    }

    private final HttpServer server;
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

    /** Authorization header -> cart lines (each: productId, name, quantity, status, message). */
    private final Map<String, List<Map<String, Object>>> carts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cartPricesUpToDate = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> products = new ConcurrentHashMap<>();
    private final Map<UUID, Stock> stock = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<Service, Boolean> down = new ConcurrentHashMap<>();
    private final AtomicInteger cartClears = new AtomicInteger();
    private final AtomicInteger reserveCalls = new AtomicInteger();
    private final List<String> inventoryAuthHeaders = new CopyOnWriteArrayList<>();

    private StubServices(HttpServer server) {
        this.server = server;
    }

    public static StubServices start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
            StubServices stub = new StubServices(server);
            server.createContext("/api/v1/cart", stub::handleCart);
            server.createContext("/api/v1/products/lookup", stub::handleLookup);
            server.createContext("/api/v1/inventory/reservations", stub::handleReservations);
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
        carts.clear();
        cartPricesUpToDate.clear();
        products.clear();
        stock.clear();
        reservations.clear();
        down.clear();
        cartClears.set(0);
        reserveCalls.set(0);
        inventoryAuthHeaders.clear();
    }

    // ---------- Test setup ----------

    /** An ACTIVE product with stock. */
    public UUID product(String sku, String listPrice, String sellingPrice, int stockOnHand) {
        UUID id = UUID.randomUUID();
        Map<String, Object> p = new ConcurrentHashMap<>();
        p.put("id", id.toString());
        p.put("sku", sku);
        p.put("name", "Product " + sku);
        p.put("price", new BigDecimal(listPrice));
        p.put("sellingPrice", new BigDecimal(sellingPrice));
        p.put("currency", "INR");
        p.put("status", "ACTIVE");
        p.put("imageUrls", List.of("https://cdn.example.com/" + sku.toLowerCase() + ".jpg"));
        products.put(id, p);
        stock.put(id, new Stock(stockOnHand));
        return id;
    }

    public void setSellingPrice(UUID productId, String sellingPrice) {
        products.get(productId).put("sellingPrice", new BigDecimal(sellingPrice));
    }

    public void discontinue(UUID productId) {
        products.remove(productId);
    }

    /** Puts a line in the cart of the user behind this Authorization header (status AVAILABLE). */
    public void addToCart(String authorization, UUID productId, int quantity) {
        addToCart(authorization, productId, quantity, "AVAILABLE", null);
    }

    public void addToCart(String authorization, UUID productId, int quantity, String status, String message) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("productId", productId.toString());
        line.put("name", products.containsKey(productId) ? products.get(productId).get("name") : "Gone product");
        line.put("quantity", quantity);
        line.put("status", status);
        line.put("message", message);
        carts.computeIfAbsent(authorization, k -> new CopyOnWriteArrayList<>()).add(line);
    }

    public void setCartPricesUpToDate(String authorization, boolean upToDate) {
        cartPricesUpToDate.put(authorization, upToDate);
    }

    public void setDown(Service service, boolean isDown) {
        down.put(service, isDown);
    }

    // ---------- Assertions ----------

    public int cartSize(String authorization) {
        return carts.getOrDefault(authorization, List.of()).size();
    }

    public int cartClears() {
        return cartClears.get();
    }

    public int reserveCalls() {
        return reserveCalls.get();
    }

    public Stock stock(UUID productId) {
        return stock.get(productId);
    }

    public Reservation reservation(String reference) {
        return reservations.get(reference);
    }

    public int reservationCount() {
        return reservations.size();
    }

    public List<String> inventoryAuthHeaders() {
        return inventoryAuthHeaders;
    }

    // ---------- Handlers ----------

    private void handleCart(HttpExchange exchange) throws IOException {
        if (isDown(exchange, Service.CART)) {
            return;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if ("DELETE".equals(exchange.getRequestMethod())) {
            carts.remove(auth);
            cartClears.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        List<Map<String, Object>> lines = carts.getOrDefault(auth, List.of());
        boolean ready = !lines.isEmpty() && lines.stream().allMatch(l -> "AVAILABLE".equals(l.get("status")));
        boolean upToDate = cartPricesUpToDate.getOrDefault(auth, true);
        Map<String, Object> cart = new LinkedHashMap<>();
        cart.put("items", lines);
        cart.put("subtotal", BigDecimal.ZERO);
        cart.put("checkoutReady", ready && upToDate);
        cart.put("pricesUpToDate", upToDate);
        cart.put("warnings", List.of());
        respond(exchange, 200, envelope(cart));
    }

    private void handleLookup(HttpExchange exchange) throws IOException {
        if (isDown(exchange, Service.PRODUCT)) {
            return;
        }
        String query = URLDecoder.decode(String.valueOf(exchange.getRequestURI().getRawQuery()), StandardCharsets.UTF_8);
        List<Map<String, Object>> found = new ArrayList<>();
        for (String param : query.split("&")) {
            if (param.startsWith("ids=")) {
                for (String id : param.substring(4).split(",")) {
                    Map<String, Object> p = products.get(UUID.fromString(id.trim()));
                    if (p != null) {
                        found.add(p);
                    }
                }
            }
        }
        respond(exchange, 200, envelope(found));
    }

    private synchronized void handleReservations(HttpExchange exchange) throws IOException {
        if (isDown(exchange, Service.INVENTORY)) {
            return;
        }
        inventoryAuthHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        String path = exchange.getRequestURI().getPath();
        String rest = path.substring("/api/v1/inventory/reservations".length());

        if (rest.isEmpty() || rest.equals("/")) {
            reserveCalls.incrementAndGet();
            JsonNode body = json.readTree(exchange.getRequestBody());
            String reference = body.get("orderReference").asText();
            Reservation existing = reservations.get(reference);
            if (existing != null) {
                respond(exchange, 200, envelope(view(existing)));
                return;
            }
            Map<UUID, Integer> items = new LinkedHashMap<>();
            body.get("items").forEach(i -> items.merge(UUID.fromString(i.get("productId").asText()), i.get("quantity").asInt(), Integer::sum));
            List<String> problems = new ArrayList<>();
            for (Map.Entry<UUID, Integer> e : items.entrySet()) {
                Stock s = stock.get(e.getKey());
                if (s == null) {
                    respond(exchange, 400, error(400, "No inventory record for product(s): [" + e.getKey() + "]"));
                    return;
                }
                if (s.available() < e.getValue()) {
                    problems.add(products.getOrDefault(e.getKey(), Map.of("sku", "?")).get("sku")
                            + ": requested " + e.getValue() + ", available " + s.available());
                }
            }
            if (!problems.isEmpty()) {
                respond(exchange, 409, error(409, "Cannot reserve stock. " + String.join("; ", problems)));
                return;
            }
            items.forEach((id, q) -> stock.get(id).reserved += q);
            Reservation r = new Reservation(reference, items);
            reservations.put(reference, r);
            respond(exchange, 201, envelope(view(r)));
            return;
        }

        String[] parts = rest.substring(1).split("/");
        Reservation r = reservations.get(parts[0]);
        if (r == null) {
            respond(exchange, 404, error(404, "Reservation not found for order: " + parts[0]));
            return;
        }
        String action = parts.length > 1 ? parts[1] : "";
        if (action.equals("release")) {
            if (r.status.equals("CONFIRMED")) {
                respond(exchange, 409, error(409, "Reservation is already confirmed and cannot be released"));
                return;
            }
            if (r.status.equals("RESERVED")) {
                r.items.forEach((id, q) -> stock.get(id).reserved -= q);
                r.status = "RELEASED";
            }
        } else if (action.equals("confirm")) {
            if (r.status.equals("RELEASED")) {
                respond(exchange, 409, error(409, "Reservation was released and cannot be confirmed"));
                return;
            }
            if (r.status.equals("RESERVED")) {
                r.items.forEach((id, q) -> {
                    stock.get(id).reserved -= q;
                    stock.get(id).onHand -= q;
                });
                r.status = "CONFIRMED";
            }
        }
        respond(exchange, 200, envelope(view(r)));
    }

    private Map<String, Object> view(Reservation r) {
        List<Map<String, Object>> items = new ArrayList<>();
        r.items.forEach((id, q) -> items.add(Map.of("productId", id.toString(), "quantity", q)));
        return Map.of("orderReference", r.reference, "status", r.status, "items", items);
    }

    private boolean isDown(HttpExchange exchange, Service service) throws IOException {
        if (down.getOrDefault(service, false)) {
            respond(exchange, 503, error(503, "Service unavailable"));
            return true;
        }
        return false;
    }

    private static Map<String, Object> envelope(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "ok");
        body.put("data", data);
        return body;
    }

    private static Map<String, Object> error(int status, String message) {
        return Map.of("status", status, "message", message);
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
