package com.ecommerce.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The real Eureka server on a random port: login required, and services can register and be found. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DiscoveryServerTest {

    private static final String USERNAME = "test-eureka";
    private static final String PASSWORD = "test-eureka-secret";

    /** What a service sends when it registers (the same JSON Spring Cloud's Eureka client sends). */
    private static final String REGISTRATION = """
            {
              "instance": {
                "instanceId": "test-service:9999",
                "hostName": "localhost",
                "app": "TEST-SERVICE",
                "ipAddr": "127.0.0.1",
                "vipAddress": "test-service",
                "secureVipAddress": "test-service",
                "status": "UP",
                "port": {"$": 9999, "@enabled": "true"},
                "securePort": {"$": 443, "@enabled": "false"},
                "homePageUrl": "http://localhost:9999/",
                "statusPageUrl": "http://localhost:9999/actuator/info",
                "healthCheckUrl": "http://localhost:9999/actuator/health",
                "dataCenterInfo": {
                  "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                  "name": "MyOwn"
                }
              }
            }
            """;

    @Autowired
    private TestRestTemplate restTemplate;

    private TestRestTemplate loggedIn() {
        return restTemplate.withBasicAuth(USERNAME, PASSWORD);
    }

    private static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    @Test
    void registry_withoutLogin_returns401() {
        ResponseEntity<String> response = restTemplate.exchange("/eureka/apps", HttpMethod.GET,
                new HttpEntity<>(json()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registry_withWrongPassword_returns401() {
        ResponseEntity<String> response = restTemplate.withBasicAuth(USERNAME, "wrong")
                .exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(json()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void dashboard_needsLogin() {
        assertThat(restTemplate.getForEntity("/", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> page = loggedIn().getForEntity("/", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Instances currently registered with Eureka");
    }

    @Test
    void health_isPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void service_canRegister_andIsThenListed() {
        // POST works without a CSRF token (services aren't browsers)
        ResponseEntity<String> registered = loggedIn().exchange("/eureka/apps/TEST-SERVICE", HttpMethod.POST,
                new HttpEntity<>(REGISTRATION, json()), String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> app = loggedIn().exchange("/eureka/apps/TEST-SERVICE", HttpMethod.GET,
                new HttpEntity<>(json()), String.class);
        assertThat(app.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(app.getBody()).contains("test-service:9999").contains("\"UP\"");

        // ...and it can deregister (what a service does when it shuts down)
        ResponseEntity<String> removed = loggedIn().exchange("/eureka/apps/TEST-SERVICE/test-service:9999",
                HttpMethod.DELETE, new HttpEntity<>(json()), String.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registration_withoutLogin_isRejected() {
        ResponseEntity<String> response = restTemplate.exchange("/eureka/apps/TEST-SERVICE", HttpMethod.POST,
                new HttpEntity<>(REGISTRATION, json()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
