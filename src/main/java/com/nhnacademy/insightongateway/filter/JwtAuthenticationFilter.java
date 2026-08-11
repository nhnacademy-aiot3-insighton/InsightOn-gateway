package com.nhnacademy.insightongateway.filter;

import com.nhnacademy.insightongateway.auth.TokenBlacklistChecker;
import com.nhnacademy.insightongateway.common.SecurityConstants;
import com.nhnacademy.insightongateway.auth.RedisTokenBlacklistChecker;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@NullMarked
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtParser jwtParser;
    private final TokenBlacklistChecker tokenBlacklistChecker;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtParser jwtParser,
                                   RedisTokenBlacklistChecker redisTokenBlacklistChecker) {
        this.jwtParser = jwtParser;
        this.tokenBlacklistChecker = redisTokenBlacklistChecker;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isExcludePath(path)) {
            return chain.filter(stripTrustedHeaders(exchange));
        }
        String token = extractToken(exchange.getRequest());
        if (Objects.isNull(token)) {
            return unauthorized(exchange, "MISSING_TOKEN");
        }
        Claims claims;
        try {
            claims = jwtParser.parseSignedClaims(token).getPayload(); // 서명 검증 + 만료 확인 (SignatureException, ExpiredJwtException 발생 가능)
        } catch (JwtException e) {
            return unauthorized(exchange, "INVALID_TOKEN");
        }
        String jti = claims.getId(); // 토큰 고유 식별자
        if (jti == null || jti.isBlank()) {
            return unauthorized(exchange, "MISSING_JTI");
        }

        return tokenBlacklistChecker.isBlacklisted(jti)
                .flatMap(blacklisted -> {
                    boolean revoked = blacklisted;
                    if (revoked) {
                        return unauthorized(exchange, "TOKEN_REVOKED");
                    }
                    return chain.filter(mutateExchange(exchange, claims));
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // 401
        exchange.getResponse().getHeaders().add("X-Auth-Error", reason);
        return exchange.getResponse().setComplete();
    }

    private boolean isExcludePath(String path) {
        return SecurityConstants.EXCLUDED_PATHS
                .stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private @Nullable String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (Objects.nonNull(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private ServerWebExchange mutateExchange(ServerWebExchange exchange, Claims claims) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        requestBuilder.headers(this::removeTrustedHeaders);

        String userId = claims.getSubject();
        if (userId != null) {
            requestBuilder.header("X-User-Id", userId);
        }

        String role = claims.get("role", String.class);
        if (ADMIN_ROLE.equals(role)) {
            requestBuilder.header("X-User-Role", ADMIN_ROLE);
        }

        return exchange.mutate().request(requestBuilder.build()).build();
    }

    private ServerWebExchange stripTrustedHeaders(ServerWebExchange exchange) {
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(this::removeTrustedHeaders)
                .build();
        return exchange.mutate().request(strippedRequest).build();
    }

    private void removeTrustedHeaders(HttpHeaders headers) {
        headers.remove("X-User-Id");
        headers.remove("X-User-Role");
    }
}
