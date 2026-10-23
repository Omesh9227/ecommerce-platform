package com.ecommerce.cartservice.integration;

import com.ecommerce.cartservice.support.StubProductService;
import com.ecommerce.cartservice.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + the real security filter chain + a real local Redis (database 15, emptied before
 * each test) + a stub product-service. Start Redis first: brew services start redis
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String CART_URL = "/api/v1/cart";
    protected static final String ITEMS_URL = "/api/v1/cart/items";
    private static final int TEST_REDIS_DATABASE = 15;

    /** Shared by all test classes in this JVM, like the cached Spring context; stops with the JVM. */
    protected static final StubProductService productService = StubProductService.start();

    @DynamicPropertySource
    static void productServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("services.product-service.url", productService::baseUrl);
    }

    protected final UUID userId = UUID.randomUUID();
    protected final UUID otherUserId = UUID.randomUUID();

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected StringRedisTemplate redis;
    @Value("${spring.data.redis.database}")
    private int redisDatabase;

    @BeforeEach
    void cleanState() {
        if (redisDatabase != TEST_REDIS_DATABASE) {
            throw new IllegalStateException("Refusing to empty Redis database " + redisDatabase
                    + "; integration tests must use database " + TEST_REDIS_DATABASE);
        }
        try (RedisConnection connection = redis.getRequiredConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        productService.reset();
    }

    protected String auth(UUID user) {
        return "Bearer " + TestTokens.customer(user);
    }

    protected String userAuth() {
        return auth(userId);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected ResultActions addItem(UUID user, UUID productId, int quantity) throws Exception {
        return mockMvc.perform(post(ITEMS_URL)
                .header(HttpHeaders.AUTHORIZATION, auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"));
    }

    /** The "data" of GET /api/v1/cart for this user. */
    protected JsonNode cart(UUID user) throws Exception {
        MvcResult result = mockMvc.perform(get(CART_URL).header(HttpHeaders.AUTHORIZATION, auth(user)))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("data");
    }
}
