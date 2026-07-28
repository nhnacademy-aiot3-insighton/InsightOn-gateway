package com.nhnacademy.insightongateway.loadbalancer;

import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@NullMarked
public class ResponseTimeTrackingFilter implements GlobalFilter, Ordered {

    private static final String TARGET_ROUTE_ID = "ai-route";

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
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (Objects.isNull(route) || !TARGET_ROUTE_ID.equals(route.getId())) {
            return;
        }
        Response<ServiceInstance> lbResponse = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
        if (Objects.isNull(lbResponse) || lbResponse.hasServer()) {
            return;
        }
        ServiceInstance instance = lbResponse.getServer();
        long elapsed = System.currentTimeMillis() - start;
        if (instance != null) {
            registry.record(instanceKey(instance), elapsed);
        }
    }

    private String instanceKey(ServiceInstance instance) {
        return instance.getInstanceId() != null
                ? instance.getInstanceId()
                : instance.getHost() + ":" + instance.getPort();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
