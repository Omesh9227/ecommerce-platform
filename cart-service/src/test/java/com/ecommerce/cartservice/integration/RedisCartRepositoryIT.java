package com.ecommerce.cartservice.integration;

import com.ecommerce.cartservice.model.CartLine;
import com.ecommerce.cartservice.repository.CartRepository;
import com.ecommerce.cartservice.repository.CartRepository.AddOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The Redis storage and its Lua scripts, against a real Redis. */
class RedisCartRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private CartRepository cartRepository;

    private static CartLine snapshot(UUID productId, String name, String price, Instant addedAt) {
        return new CartLine(productId, "SKU", name, null, new BigDecimal(price), "INR", 0, addedAt);
    }

    @Test
    void add_createsLine_thenIncreases_keepingFirstAddedAt_andRefreshingSnapshot() {
        UUID product = UUID.randomUUID();
        Instant first = Instant.parse("2026-10-23T08:00:00Z");

        assertThat(cartRepository.add(userId, snapshot(product, "Old name", "100.10", first), 2, 10, 50))
                .isEqualTo(new CartRepository.AddResult(AddOutcome.ADDED, 2));
        assertThat(cartRepository.add(userId, snapshot(product, "New name", "99.99", first.plusSeconds(60)), 3, 10, 50))
                .isEqualTo(new CartRepository.AddResult(AddOutcome.ADDED, 5));

        CartLine line = cartRepository.find(userId, product).orElseThrow();
        assertThat(line.quantity()).isEqualTo(5);
        assertThat(line.addedAt()).isEqualTo(first);
        assertThat(line.name()).isEqualTo("New name");
        assertThat(line.priceWhenAdded()).isEqualByComparingTo("99.99");
        assertThat(line.imageUrl()).isNull();
    }

    @Test
    void add_enforcesQuantityAndItemLimits() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Instant now = Instant.now();

        cartRepository.add(userId, snapshot(a, "A", "1.00", now), 4, 5, 2);
        assertThat(cartRepository.add(userId, snapshot(a, "A", "1.00", now), 2, 5, 2).outcome())
                .isEqualTo(AddOutcome.QUANTITY_LIMIT);
        cartRepository.add(userId, snapshot(b, "B", "1.00", now), 1, 5, 2);
        assertThat(cartRepository.add(userId, snapshot(c, "C", "1.00", now), 1, 5, 2).outcome())
                .isEqualTo(AddOutcome.CART_FULL);
        // A product already in a full cart can still be increased
        assertThat(cartRepository.add(userId, snapshot(a, "A", "1.00", now), 1, 5, 2).quantity()).isEqualTo(5);

        assertThat(cartRepository.findAll(userId)).hasSize(2);
    }

    @Test
    void setQuantity_removeAndClear() {
        UUID a = UUID.randomUUID();
        cartRepository.add(userId, snapshot(a, "A", "1.00", Instant.now()), 1, 10, 50);

        assertThat(cartRepository.setQuantity(userId, a, 7)).isTrue();
        assertThat(cartRepository.find(userId, a).orElseThrow().quantity()).isEqualTo(7);
        assertThat(cartRepository.setQuantity(userId, UUID.randomUUID(), 7)).isFalse();

        assertThat(cartRepository.remove(userId, a)).isTrue();
        assertThat(cartRepository.remove(userId, a)).isFalse();

        cartRepository.add(userId, snapshot(a, "A", "1.00", Instant.now()), 1, 10, 50);
        cartRepository.clear(userId);
        assertThat(cartRepository.findAll(userId)).isEmpty();
    }

    @Test
    void unreadableLine_isSkipped_notFatal() {
        UUID a = UUID.randomUUID();
        cartRepository.add(userId, snapshot(a, "A", "1.00", Instant.now()), 1, 10, 50);
        redis.opsForHash().put("cart:" + userId, UUID.randomUUID().toString(), "{not json");

        assertThat(cartRepository.findAll(userId)).extracting(CartLine::productId).containsExactly(a);
    }
}
