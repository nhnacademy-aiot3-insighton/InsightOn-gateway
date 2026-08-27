package com.nhnacademy.insightongateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

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
        return appender.list.getLast().getFormattedMessage();
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
        assertThat(message)
                .contains("GET")
                .contains("/api/v1/reports/1")
                .contains("status=200")
                .doesNotContain("CANCELED")
                .doesNotContain("authError=");
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
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        GatewayFilterChain chain = ex -> Mono.never();

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

    @Test
    void pathWithEncodedCrlf_doesNotForgeLogLines() throws Exception {
        // URI(String) 생성자는 이미 percent-encode된 문자열을 다시 인코딩하지 않으므로,
        // getRawPath()는 "%0D%0A"를 그대로 유지하지만 getPath()는 실제 CR/LF로 디코드해버린다.
        // 그대로 로그에 쓰면 가짜 로그 라인을 주입(log forging)할 수 있다.
        URI uri = new URI("http://localhost/api/v1/reports/1%0D%0Aforged-status=200");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, uri).build());
        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String message = lastMessage();
        assertThat(message).doesNotContain("\r").doesNotContain("\n");
        assertThat(appender.list).hasSize(1);
    }
}
