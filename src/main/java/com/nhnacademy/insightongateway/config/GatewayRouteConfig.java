package com.nhnacademy.insightongateway.config;

import com.nhnacademy.insightongateway.common.properties.GatewayRouteProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, GatewayRouteProperties gatewayRouteProperties) {
        return builder.routes()
                .route("auth-route", r -> r
                        .path(
                                "/api/v1/users/**",
                                "/api/v1/auth/**",
                                "/api/v1/admin/**"
                        )
                        .uri(gatewayRouteProperties.auth()))

                // Core route 변경
                // location, actuator, dashboards가 group/{group-id} 하위에 묶여있어서 제거
                .route("core-route", r -> r
                        .path(
                                "/api/v1/groups/**",
                                "/api/v1/gateways/**",
                                "/api/v1/sensor/**",
                                "/api/v1/weather/**",
                                "/api/v1/regions/**",
                                "/api/v1/group-registrations/**"
                        )
                        .uri(gatewayRouteProperties.core()))

                .route("ai-route", r -> r
                        .path(
                                "/api/v1/reports/**",
                                "/api/v1/suggestions/**",
                                "/api/v1/hourly-telemetry-stats",
                                "/api/v1/dashboard-notifications",
                                "/api/v1/engine-alerts/**",
                                "/api/v1/chat"
                        )
                        .uri(gatewayRouteProperties.ai()))

                .route("ruleengine-route", r -> r
                        .path(
                                "/api/v1/flows/**"
                        )
                        .uri(gatewayRouteProperties.rule()))

                .route("auth-api-docs", r -> r
                        .path("/auth/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri(gatewayRouteProperties.auth()))

                .route("core-api-docs", r -> r
                        .path("/core/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri(gatewayRouteProperties.core()))

                .route("ai-api-docs", r -> r
                        .path("/ai/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri(gatewayRouteProperties.ai()))

                .route("ruleengine-api-docs", r -> r
                        .path("/ruleengine/v3/api-docs")
                        .filters(f -> f.stripPrefix(1))
                        .uri(gatewayRouteProperties.rule()))

                .build();
    }
}
