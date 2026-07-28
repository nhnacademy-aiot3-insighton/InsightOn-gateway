package com.nhnacademy.insightongateway.loadbalancer;

import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@NullMarked // 이 클래스 안의 모든 파라미터/리턴 타입은 기본적으로 non-null
public class ResponseTimeTrackingFilter implements GatewayFilter {

    private final ResponseTimeRegistry registry;

    public ResponseTimeTrackingFilter(ResponseTimeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        return chain.filter(exchange)
                .doFinally(signalType -> recordIdAiRoute(exchange, start));
    }

    private void recordIdAiRoute(ServerWebExchange exchange, long start) {
        // Response: 로드밸런서가 인스턴스를 골라낸 결과
        // 로드밸런싱을 시도했는데 실패할 수도 있음, 실패해서 인스턴스가 없음을 구분할 방법이 필요하고 그것이 hasServer()임
        Response<ServiceInstance> lbResponse = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
        if (Objects.isNull(lbResponse) || !lbResponse.hasServer()) {
            return;
        }
        ServiceInstance instance = lbResponse.getServer();
        long elapsed = System.currentTimeMillis() - start;
        if (instance != null) {
            registry.recordResponseTime(instanceKey(instance), elapsed);
        }
    }

    private String instanceKey(ServiceInstance instance) {
        return instance.getInstanceId() != null
                ? instance.getInstanceId()
                : instance.getHost() + ":" + instance.getPort();
    }
}
