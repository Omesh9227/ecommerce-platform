package com.ecommerce.wishlistservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the outbox relay's polling schedule. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
