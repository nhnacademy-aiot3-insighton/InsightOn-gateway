package com.nhnacademy.insightongateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logbackLogger().addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger().detachAppender(appender);
    }

    private Logger logbackLogger() {
        return (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
    }

    private ServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private String lastMessage() {
        assertThat(appender.list).isNotEmpty();
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    @Test
    void normalCompletion_logsMethodPathStatusAndElapsedTime() {
        ServerWebExchange exchange = exchange("/api/v1/reports/1");
        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String message = lastMessage();
        assertThat(message).contains("GET").contains("/api/v1/reports/1").contains("status=200");
        assertThat(message).doesNotContain("CANCELED");
        assertThat(message).doesNotContain("authError=");
    }

    @Test
    void chainError_logsOnErrorSignalAndPropagatesError() {
        ServerWebExchange exchange = exchange("/api/v1/reports/1");
        RuntimeException boom = new RuntimeException("boom");
        GatewayFilterChain chain = ex -> Mono.error(boom);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(e -> e == boom)
                .verify();

        assertThat(lastMessage()).contains("onError");
    }

    @Test
    void clientCancels_logsCanceledWithDisconnectedMarker() {
        ServerWebExchange exchange = exchange("/api/v1/dashboard-notifications/stream");
        exchange.getResponse().setStatusCode(HttpStatus.OK); // 이미 커밋된 SSE 응답을 흉내
        GatewayFilterChain chain = ex -> Mono.never(); // 클라이언트가 끊기 전까지 계속 열려있는 스트림을 흉내

        StepVerifier.create(filter.filter(exchange, chain))
                .thenCancel()
                .verify();

        String message = lastMessage();
        assertThat(message).contains("CANCELED").contains("client disconnected").contains("status=200");
    }

    @Test
    void authErrorHeaderPresent_isAppendedToLog() {
        ServerWebExchange exchange = exchange("/api/v1/protected");
        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            ex.getResponse().getHeaders().add("X-Auth-Error", "MISSING_TOKEN");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(lastMessage()).contains("status=401").contains("authError=MISSING_TOKEN");
    }

    @Test
    void noAuthErrorHeader_omitsAuthErrorFromLog() {
        ServerWebExchange exchange = exchange("/api/v1/reports/1");
        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(lastMessage()).doesNotContain("authError=");
    }
}
