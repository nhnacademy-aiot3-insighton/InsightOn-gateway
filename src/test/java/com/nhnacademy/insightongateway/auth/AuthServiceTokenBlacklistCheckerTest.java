package com.nhnacademy.insightongateway.auth;

import com.nhnacademy.insightongateway.common.properties.GatewayRouteProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

class AuthServiceTokenBlacklistCheckerTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startStubServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/auth/tokens", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private AuthServiceTokenBlacklistChecker checkerFor(String baseUrl) {
        return new AuthServiceTokenBlacklistChecker(new GatewayRouteProperties(baseUrl, null, null, null));
    }

    @Test
    void blacklistedTrue_returnsTrue() throws IOException {
        String baseUrl = startStubServer(200, "{\"blacklisted\": true}");
        AuthServiceTokenBlacklistChecker checker = checkerFor(baseUrl);

        StepVerifier.create(checker.isBlacklisted("jti-1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void blacklistedFalse_returnsFalse() throws IOException {
        String baseUrl = startStubServer(200, "{\"blacklisted\": false}");
        AuthServiceTokenBlacklistChecker checker = checkerFor(baseUrl);

        StepVerifier.create(checker.isBlacklisted("jti-1"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void authServiceReturns5xx_failsClosed() throws IOException {
        String baseUrl = startStubServer(500, "{}");
        AuthServiceTokenBlacklistChecker checker = checkerFor(baseUrl);

        StepVerifier.create(checker.isBlacklisted("jti-1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void authServiceUnreachable_failsClosed() {
        // 아무 서버도 떠 있지 않은 주소로 요청 -> 연결 자체가 실패하는 케이스
        AuthServiceTokenBlacklistChecker checker = checkerFor("http://localhost:1");

        StepVerifier.create(checker.isBlacklisted("jti-1"))
                .expectNext(true)
                .verifyComplete();
    }
}
