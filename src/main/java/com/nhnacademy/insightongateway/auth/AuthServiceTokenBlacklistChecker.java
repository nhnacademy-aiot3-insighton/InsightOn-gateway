package com.nhnacademy.insightongateway.auth;

import com.nhnacademy.insightongateway.common.properties.GatewayRouteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class AuthServiceTokenBlacklistChecker {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceTokenBlacklistChecker.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final WebClient webClient;

    public AuthServiceTokenBlacklistChecker(GatewayRouteProperties gatewayRouteProperties) {
        this.webClient = WebClient.builder().baseUrl(gatewayRouteProperties.auth()).build();
    }

    public Mono<Boolean> isBlacklisted(String jti) {
        return webClient.get()
                .uri("/api/v1/auth/tokens/{jti}/blacklisted", jti)
                .retrieve()
                .bodyToMono(BlacklistStatusResponse.class)
                .map(BlacklistStatusResponse::blacklisted)
                .timeout(TIMEOUT)
                // auth 서비스 장애/타임아웃 시 fail-closed: 확인 불가한 토큰은 일단 거부한다
                .onErrorResume(e -> {
                    log.warn("[AuthServiceTokenBlacklistChecker] auth 서비스 블랙리스트 확인 실패, fail-closed 처리 (jti={})", jti, e);
                    return Mono.just(true);
                });
    }

    private record BlacklistStatusResponse(boolean blacklisted) {
    }
}
