package com.ecommerce.wishlistservice.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WishlistStoreTest {

    @Test
    void lockKey_isStablePerUser_andDiffersBetweenUsers() {
        UUID user = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

        assertThat(WishlistStore.lockKey(user)).isEqualTo(WishlistStore.lockKey(UUID.fromString(user.toString())));
        assertThat(WishlistStore.lockKey(user)).isNotEqualTo(WishlistStore.lockKey(UUID.randomUUID()));
    }
}
