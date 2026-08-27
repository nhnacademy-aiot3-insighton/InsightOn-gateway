package com.nhnacademy.insightongateway.filter;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.time.Instant;

@Component
@NullMarked
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Instant start = Instant.now();

        return chain.filter(exchange)
                .doFinally(signalType -> logAccess(exchange, request, start, signalType));
    }

    private void logAccess(ServerWebExchange exchange, ServerHttpRequest request, Instant start,
                            SignalType signalType) {
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        String authError = exchange.getResponse().getHeaders().getFirst("X-Auth-Error");
        String authErrorSuffix = authError == null ? "" : " authError=" + sanitize(authError);

        String path = sanitize(request.getURI().getRawPath());

        if (signalType == SignalType.CANCEL) {
            log.info("[AccessLog] {} {} CANCELED status={} elapsedMs={}{} (client disconnected)",
                    request.getMethod(), path, status, elapsedMs, authErrorSuffix);
        } else {
            log.info("[AccessLog] {} {} {} status={} elapsedMs={}{}",
                    request.getMethod(), path, signalType, status, elapsedMs, authErrorSuffix);
        }
    }

    private String sanitize(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    @Override
    public int getOrder() {
        // JwtAuthenticationFilter보다 먼저 실행되어 401 즉시 반환 요청도 감싸서 로그를 남긴다.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
