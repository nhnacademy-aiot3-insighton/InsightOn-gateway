package com.nhnacademy.insightongateway.config;

import com.nhnacademy.insightongateway.loadbalancer.ResponseTimeRegistry;
import com.nhnacademy.insightongateway.filter.ResponseTimeTrackingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    private final ResponseTimeRegistry registry;

    public GatewayRouteConfig(ResponseTimeRegistry registry) {
        this.registry = registry;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-route", r -> r
                        .path(
                                "/api/v1/user/**",
                                "/api/v1/auth/**",
                                "/api/v1/admin/users/**"
                        )
                        .uri("lb://INSIGHTON-AUTH"))

                .route("core-route", r -> r
                        .path(
                                "/api/v1/groups/**",
                                "/api/v1/locations/**",
                                "/api/v1/gateways/**",
                                "/api/v1/devices/**",
                                "/api/v1/actuators/**",
                                "/api/v1/metric-definitions",
                                "/api/v1/dashboards/**",
                                "/api/v1/widgets/**"
                        )
                        .uri("lb://INSIGHTON-CORE"))

                .route("ai-route", r -> r
                        .path(
                                "/api/v1/reports/**",
                                "/api/v1/suggestions/**",
                                "/api/v1/hourly-telemetry-stats",
                                "/api/v1/dashboard-notifications",
                                "/api/v1/engine-alerts/**",
                                "/api/v1/chat"
                        )
                        .filters(gatewayFilterSpec ->  gatewayFilterSpec.filter(new ResponseTimeTrackingFilter(registry)))
                        .uri("lb://INSIGHTON-AI"))

                .route("ruleengine-route", r -> r
                        .path(
                                "/api/v1/flows/**"
                        )
                        .uri("lb://INSIGHTON-RULEENGINE"))

                .route("auth-api-docs", r -> r
                        .path("/auth/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://INSIGHTON-AUTH"))

                .route("core-api-docs", r -> r
                        .path("/core/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://INSIGHTON-CORE"))

                .route("ai-api-docs", r -> r
                        .path("/ai/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://INSIGHTON-AI"))

                .route("ruleengine-api-docs", r -> r
                        .path("/ruleengine/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://INSIGHTON-RULEENGINE"))


                .build();
    }
}
