package com.ecommerce.cartservice.repository;

import com.ecommerce.cartservice.config.CartProperties;
import com.ecommerce.cartservice.config.RedisScriptsConfig.CartScripts;
import com.ecommerce.cartservice.model.CartLine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Each cart is one Redis hash:
 * <pre>
 *   key   cart:{userId}
 *   field productId  ->  CartLine as JSON
 *   TTL   app.cart.ttl (restarted on every view or change)
 * </pre>
 * Adds and quantity changes go through Lua scripts so their checks and writes are atomic.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisCartRepository implements CartRepository {

    static final String KEY_PREFIX = "cart:";

    private static final long CART_FULL = -1;
    private static final long OVER_MAX_QUANTITY = -2;
    private static final long NOT_IN_CART = -1;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CartProperties cartProperties;
    private final CartScripts scripts;

    static String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    @Override
    public List<CartLine> findAll(UUID userId) {
        String key = key(userId);
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return List.of();
        }
        redis.expire(key, cartProperties.ttl());

        List<CartLine> lines = new ArrayList<>(entries.size());
        entries.forEach((field, json) -> read(key, String.valueOf(field), String.valueOf(json)).ifPresent(lines::add));
        return lines;
    }

    @Override
    public Optional<CartLine> find(UUID userId, UUID productId) {
        String key = key(userId);
        Object json = redis.opsForHash().get(key, productId.toString());
        return json == null ? Optional.empty() : read(key, productId.toString(), json.toString());
    }

    @Override
    public AddResult add(UUID userId, CartLine line, int quantityToAdd, int maxQuantity, int maxItems) {
        Long result = redis.execute(scripts.addItem(), List.of(key(userId)),
                line.productId().toString(),
                write(line),
                String.valueOf(quantityToAdd),
                String.valueOf(maxQuantity),
                String.valueOf(maxItems),
                String.valueOf(ttlSeconds()));

        if (result == null) {
            throw new IllegalStateException("Redis returned no result for the add-item script");
        }
        if (result == CART_FULL) {
            return new AddResult(AddOutcome.CART_FULL, 0);
        }
        if (result == OVER_MAX_QUANTITY) {
            return new AddResult(AddOutcome.QUANTITY_LIMIT, 0);
        }
        return new AddResult(AddOutcome.ADDED, result.intValue());
    }

    @Override
    public boolean setQuantity(UUID userId, UUID productId, int quantity) {
        Long result = redis.execute(scripts.setQuantity(), List.of(key(userId)),
                productId.toString(), String.valueOf(quantity), String.valueOf(ttlSeconds()));
        return result != null && result != NOT_IN_CART;
    }

    @Override
    public boolean remove(UUID userId, UUID productId) {
        Long removed = redis.opsForHash().delete(key(userId), productId.toString());
        return removed != null && removed > 0;
    }

    @Override
    public void clear(UUID userId) {
        redis.delete(key(userId));
    }

    private long ttlSeconds() {
        return cartProperties.ttl().toSeconds();
    }

    private String write(CartLine line) {
        try {
            return objectMapper.writeValueAsString(line);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise cart line for product " + line.productId(), e);
        }
    }

    /** A line that can't be read (e.g. written by an incompatible old version) is skipped, not fatal. */
    private Optional<CartLine> read(String key, String field, String json) {
        try {
            return Optional.of(objectMapper.readValue(json, CartLine.class));
        } catch (JsonProcessingException e) {
            log.warn("Skipping unreadable cart line {} / {}: {}", key, field, e.getOriginalMessage());
            return Optional.empty();
        }
    }
}
