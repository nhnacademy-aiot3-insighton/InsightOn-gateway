package com.nhnacademy.insightongateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRouteConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    private Map<String, Route> routesById;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        registry.add("jwt.public-key-base64", () -> Base64.getEncoder().encodeToString(pem.getBytes()));
        registry.add("gateway-route.auth", () -> "http://localhost:8000");
        registry.add("gateway-route.ai", () -> "http://localhost:8100");
        registry.add("gateway-route.rule", () -> "http://localhost:8200");
        registry.add("gateway-route.core", () -> "http://localhost:8300");
    }

    @BeforeEach
    void setUp() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        routesById = routes.stream().collect(Collectors.toMap(Route::getId, route -> route));
    }

    @Test
    void allExpectedRoutesAreDefined() {
        assertThat(routesById.keySet()).containsExactlyInAnyOrder(
                "auth-route", "core-route", "ai-route", "ruleengine-route",
                "auth-api-docs", "core-api-docs", "ai-api-docs", "ruleengine-api-docs"
        );
    }

    @Test
    void routesPointToTheConfiguredServiceUris() {
        assertThat(route("auth-route").getUri()).hasToString("http://localhost:8000");
        assertThat(route("core-route").getUri()).hasToString("http://localhost:8300");
        assertThat(route("ai-route").getUri()).hasToString("http://localhost:8100");
        assertThat(route("ruleengine-route").getUri()).hasToString("http://localhost:8200");

        assertThat(route("auth-api-docs").getUri()).hasToString("http://localhost:8000");
        assertThat(route("core-api-docs").getUri()).hasToString("http://localhost:8300");
        assertThat(route("ai-api-docs").getUri()).hasToString("http://localhost:8100");
        assertThat(route("ruleengine-api-docs").getUri()).hasToString("http://localhost:8200");
    }

    @Test
    void authRoute_matchesAuthUserAndAdminPaths() {
        assertMatches("auth-route", "/api/v1/auth/login");
        assertMatches("auth-route", "/api/v1/users/me");
        assertMatches("auth-route", "/api/v1/admin/users/1");
        assertDoesNotMatch("auth-route", "/api/v1/groups/1");
    }

    @Test
    void coreRoute_matchesCorePaths() {
        assertMatches("core-route", "/api/v1/groups/1");
        assertMatches("core-route", "/api/v1/gateways/1");
        assertMatches("core-route", "/api/v1/sensor/1");
        assertMatches("core-route", "/api/v1/weather/1");
        assertMatches("core-route", "/api/v1/regions/1");
        assertMatches("core-route", "/api/v1/group-registrations/1");
        assertDoesNotMatch("core-route", "/api/v1/auth/login");
    }

    @Test
    void aiRoute_matchesAiPaths() {
        assertMatches("ai-route", "/api/v1/reports/1");
        assertMatches("ai-route", "/api/v1/suggestions/1");
        assertMatches("ai-route", "/api/v1/hourly-telemetry-stats");
        assertMatches("ai-route", "/api/v1/dashboard-notifications");
        assertMatches("ai-route", "/api/v1/engine-alerts/1");
        assertMatches("ai-route", "/api/v1/chat");
        assertDoesNotMatch("ai-route", "/api/v1/flows/1");
    }

    @Test
    void ruleengineRoute_matchesFlowsPath() {
        assertMatches("ruleengine-route", "/api/v1/flows/1");
        assertDoesNotMatch("ruleengine-route", "/api/v1/chat");
    }

    @Test
    void apiDocsRoutes_matchOnlyTheirOwnDocsPath() {
        assertMatches("auth-api-docs", "/auth/v3/api-docs");
        assertDoesNotMatch("auth-api-docs", "/core/v3/api-docs");

        assertMatches("core-api-docs", "/core/v3/api-docs");
        assertMatches("ai-api-docs", "/ai/v3/api-docs");
        assertMatches("ruleengine-api-docs", "/ruleengine/v3/api-docs");
    }

    private Route route(String id) {
        Route route = routesById.get(id);
        if (route == null) {
            throw new NoSuchElementException("route not found: " + id);
        }
        return route;
    }

    private void assertMatches(String routeId, String path) {
        assertThat(pathMatches(routeId, path))
                .as("route '%s' should match path '%s'", routeId, path)
                .isTrue();
    }

    private void assertDoesNotMatch(String routeId, String path) {
        assertThat(pathMatches(routeId, path))
                .as("route '%s' should NOT match path '%s'", routeId, path)
                .isFalse();
    }

    private boolean pathMatches(String routeId, String path) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return Boolean.TRUE.equals(Mono.from(route(routeId).getPredicate().apply(exchange)).block());
    }
}
