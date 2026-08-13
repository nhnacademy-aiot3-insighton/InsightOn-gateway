package com.nhnacademy.insightongateway.filter;

import com.nhnacademy.insightongateway.auth.AuthServiceTokenBlacklistChecker;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.KeyPair;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private KeyPair keyPair;
    private AuthServiceTokenBlacklistChecker tokenBlacklistChecker;
    private JwtAuthenticationFilter filter;
    private AtomicReference<ServerWebExchange> capturedExchange;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        keyPair = Jwts.SIG.RS256.keyPair().build();
        JwtParser jwtParser = Jwts.parser().verifyWith(keyPair.getPublic()).build();
        tokenBlacklistChecker = mock(AuthServiceTokenBlacklistChecker.class);
        filter = new JwtAuthenticationFilter(jwtParser, tokenBlacklistChecker);

        capturedExchange = new AtomicReference<>();
        chain = exchange -> {
            capturedExchange.set(exchange);
            return Mono.empty();
        };
    }

    private String token(String subject, String jti, String role) {
        JwtBuilder builder = Jwts.builder().signWith(keyPair.getPrivate());
        if (subject != null) {
            builder.subject(subject);
        }
        if (jti != null) {
            builder.id(jti);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }

    private ServerWebExchange exchangeWithAuthHeader(String authorizationHeaderValue) {
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest.get("/api/v1/protected");
        if (authorizationHeaderValue != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
        }
        return MockServerWebExchange.from(requestBuilder.build());
    }

    @ParameterizedTest(name = "[{index}] Authorization={0}")
    @MethodSource("missingTokenAuthorizationHeaders")
    void missingOrUnusableToken_returns401MissingToken(String authorizationHeaderValue) {
        ServerWebExchange exchange = exchangeWithAuthHeader(authorizationHeaderValue);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("MISSING_TOKEN");
        assertThat(capturedExchange.get()).isNull();
    }

    private static Stream<Arguments> missingTokenAuthorizationHeaders() {
        return Stream.of(
                Arguments.of((Object) null),      // Authorization 헤더 자체가 없음
                Arguments.of("Basic abcdef"),      // Bearer 접두사가 아님
                Arguments.of("Bearer    ")         // Bearer 뒤에 토큰이 없음(공백만)
        );
    }

    @Test
    void tokenSignedWithDifferentKey_returns401InvalidToken() {
        KeyPair otherKeyPair = Jwts.SIG.RS256.keyPair().build();
        String rogueToken = Jwts.builder()
                .subject("user-1")
                .id(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .signWith(otherKeyPair.getPrivate())
                .compact();

        ServerWebExchange exchange = exchangeWithAuthHeader("Bearer " + rogueToken);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("INVALID_TOKEN");
        assertThat(capturedExchange.get()).isNull();
    }

    @Test
    void expiredToken_returns401InvalidToken() {
        String expiredToken = Jwts.builder()
                .subject("user-1")
                .id(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(keyPair.getPrivate())
                .compact();

        ServerWebExchange exchange = exchangeWithAuthHeader("Bearer " + expiredToken);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("INVALID_TOKEN");
        assertThat(capturedExchange.get()).isNull();
    }

    @Test
    void tokenWithoutJti_returns401MissingJti() {
        String tokenWithoutJti = token("user-1", null, "ADMIN");

        ServerWebExchange exchange = exchangeWithAuthHeader("Bearer " + tokenWithoutJti);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("MISSING_JTI");
        assertThat(capturedExchange.get()).isNull();
    }

    @Test
    void blacklistedToken_returns401TokenRevoked() {
        String jti = UUID.randomUUID().toString();
        String validToken = token("user-1", jti, "USER");
        when(tokenBlacklistChecker.isBlacklisted(jti)).thenReturn(Mono.just(true));

        ServerWebExchange exchange = exchangeWithAuthHeader("Bearer " + validToken);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("TOKEN_REVOKED");
        assertThat(capturedExchange.get()).isNull();
    }

    @Test
    void excludedPath_stripsClientSuppliedTrustedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "attacker")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().get("X-User-Role")).isNull();
        assertThat(result.getRequest().getHeaders().get("X-User-Id")).isNull();
    }

    @Test
    void wildcardExcludedPath_bypassesAuthAndStripsTrustedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/user/oauth/callback")
                .header("X-User-Role", "ADMIN")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().get("X-User-Role")).isNull();
    }

    @Test
    void adminToken_overridesClientSuppliedRoleHeader() {
        String jti = UUID.randomUUID().toString();
        String adminToken = token("user-1", jti, "ADMIN");
        when(tokenBlacklistChecker.isBlacklisted(jti)).thenReturn(Mono.just(false));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .header("X-User-Role", "USER") // 클라이언트가 위조 시도
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().get("X-User-Role")).containsExactly("ADMIN");
        assertThat(result.getRequest().getHeaders().get("X-User-Id")).containsExactly("user-1");
    }

    @Test
    void userRoleToken_doesNotSetRoleHeader() {
        String jti = UUID.randomUUID().toString();
        String userToken = token("user-1", jti, "USER");
        when(tokenBlacklistChecker.isBlacklisted(jti)).thenReturn(Mono.just(false));

        ServerWebExchange exchange = exchangeWithAuthHeader("Bearer " + userToken);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().get("X-User-Role")).isNull();
    }

    @Test
    void tokenWithoutRoleClaim_doesNotSetRoleHeader() {
        String jti = UUID.randomUUID().toString();
        String tokenWithoutRole = token("user-1", jti, null);
        when(tokenBlacklistChecker.isBlacklisted(jti)).thenReturn(Mono.just(false));

        ServerWebExchange exchange = exchangeWithAuthHeader("Bearer " + tokenWithoutRole);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().get("X-User-Role")).isNull();
    }

    @Test
    void adminToken_setsUserIdFromSubjectClaim() {
        String jti = UUID.randomUUID().toString();
        String adminToken = token("user-42", jti, "ADMIN");
        when(tokenBlacklistChecker.isBlacklisted(jti)).thenReturn(Mono.just(false));

        ServerWebExchange exchange = exchangeWithAuthHeader("Bearer " + adminToken);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerWebExchange result = capturedExchange.get();
        assertThat(result).isNotNull();
        assertThat(result.getRequest().getHeaders().get("X-User-Id")).containsExactly("user-42");
    }
}
