package com.ecommerce.cartservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Lua scripts in src/main/resources/scripts. Redis runs each script atomically, which is what makes
 * "check the limits, then change the cart" safe when the same user sends two requests at once.
 * Spring sends them with EVALSHA (cached by Redis) and falls back to EVAL after a Redis restart.
 */
@Configuration
public class RedisScriptsConfig {

    public record CartScripts(RedisScript<Long> addItem, RedisScript<Long> setQuantity) {
    }

    @Bean
    public CartScripts cartScripts() {
        return new CartScripts(
                RedisScript.of(new ClassPathResource("scripts/cart-add-item.lua"), Long.class),
                RedisScript.of(new ClassPathResource("scripts/cart-set-quantity.lua"), Long.class));
    }
}
