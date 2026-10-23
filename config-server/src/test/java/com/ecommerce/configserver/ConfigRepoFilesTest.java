package com.ecommerce.configserver;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** config-repo/ is committed to Git, so it must never hold a secret - only ${PLACEHOLDERS} filled from .env. */
class ConfigRepoFilesTest {

    private static final Path CONFIG_REPO = Path.of("..", "config-repo");

    @Test
    void configRepo_holdsNoSecrets() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONFIG_REPO)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".properties")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    String key = trimmed.substring(0, trimmed.indexOf('=')).strip().toLowerCase(Locale.ROOT);
                    String value = trimmed.substring(trimmed.indexOf('=') + 1).strip();
                    boolean sensitive = key.contains("password") || key.contains("secret") || key.contains("token");
                    if (sensitive && !value.isEmpty() && !value.startsWith("${")) {
                        problems.add(file.getFileName() + ": " + key);
                    }
                    if (value.contains("://") && value.matches(".*://[^/${}]+:[^@${}]+@.*")) {
                        problems.add(file.getFileName() + ": password written into a URL in " + key);
                    }
                }
            }
        }
        assertThat(problems).as("secrets in config-repo").isEmpty();
    }

    @Test
    void everyServiceFile_isNamedAfterAService() throws IOException {
        List<String> services = List.of("application", "api-gateway", "user-service", "product-service",
                "inventory-service", "cart-service", "order-service", "payment-service", "notification-service");
        try (Stream<Path> files = Files.list(CONFIG_REPO)) {
            List<String> names = files.map(f -> f.getFileName().toString())
                    .filter(name -> name.endsWith(".properties"))
                    .map(name -> name.substring(0, name.length() - ".properties".length()))
                    .toList();
            // A typo in a file name (order-servce.properties) would silently never be served
            assertThat(names).isNotEmpty().allSatisfy(name -> assertThat(services).contains(name));
        }
    }
}
