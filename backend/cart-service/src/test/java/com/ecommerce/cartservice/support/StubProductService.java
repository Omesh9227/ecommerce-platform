package com.ecommerce.cartservice.support;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for product-service's GET /api/v1/products/lookup in tests.
 * Tests put products in, change their price or stock, remove them (= no longer for sale) or take the whole
 * service "down" (503).
 */
public final class StubProductService {

    private final HttpServer server;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<UUID, Map<String, Object>> products = new ConcurrentHashMap<>();
    private final AtomicBoolean down = new AtomicBoolean(false);
    private final AtomicInteger lookups = new AtomicInteger();
    private volatile String lastQuery;

    private StubProductService(HttpServer server) {
        this.server = server;
    }

    public static StubProductService start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            StubProductService stub = new StubProductService(server);
            server.createContext("/api/v1/products/lookup", stub::handleLookup);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the product-service stub", e);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    public void reset() {
        products.clear();
        down.set(false);
        lookups.set(0);
        lastQuery = null;
    }

    /** Adds or replaces an ACTIVE product. */
    public UUID put(UUID id, String sku, String name, String price, String sellingPrice, int availableQuantity) {
        Map<String, Object> product = new ConcurrentHashMap<>(); // tests change it while the server may read it
        product.put("id", id.toString());
        product.put("sku", sku);
        product.put("name", name);
        product.put("description", "Stub product");
        product.put("brand", "Acme");
        product.put("price", new BigDecimal(price));
        product.put("discountPercent", BigDecimal.ZERO);
        product.put("sellingPrice", new BigDecimal(sellingPrice));
        product.put("currency", "INR");
        product.put("status", "ACTIVE");
        product.put("availableQuantity", availableQuantity);
        product.put("inStock", availableQuantity > 0);
        product.put("imageUrls", List.of("https://cdn.example.com/" + sku.toLowerCase() + ".jpg"));
        products.put(id, product);
        return id;
    }

    public UUID put(String sku, String price, int availableQuantity) {
        return put(UUID.randomUUID(), sku, "Product " + sku, price, price, availableQuantity);
    }

    public void setSellingPrice(UUID id, String sellingPrice) {
        products.get(id).put("sellingPrice", new BigDecimal(sellingPrice));
    }

    public void setStock(UUID id, int availableQuantity) {
        products.get(id).put("availableQuantity", availableQuantity);
        products.get(id).put("inStock", availableQuantity > 0);
    }

    /** The product is no longer for sale (product-service's lookup leaves it out). */
    public void remove(UUID id) {
        products.remove(id);
    }

    public void setDown(boolean isDown) {
        down.set(isDown);
    }

    public int lookups() {
        return lookups.get();
    }

    public String lastQuery() {
        return lastQuery;
    }

    private void handleLookup(HttpExchange exchange) throws IOException {
        lookups.incrementAndGet();
        lastQuery = exchange.getRequestURI().getRawQuery();
        if (down.get()) {
            respond(exchange, 503, Map.of("status", 503, "message", "Service unavailable"));
            return;
        }

        List<Map<String, Object>> found = new ArrayList<>();
        String query = lastQuery == null ? "" : URLDecoder.decode(lastQuery, StandardCharsets.UTF_8);
        for (String param : query.split("&")) {
            if (param.startsWith("ids=")) {
                for (String id : param.substring(4).split(",")) {
                    if (!id.isBlank()) {
                        Map<String, Object> product = products.get(UUID.fromString(id.trim()));
                        if (product != null) {
                            found.add(product);
                        }
                    }
                }
            }
        }
        respond(exchange, 200, Map.of("success", true, "message", "Products fetched successfully", "data", found));
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
