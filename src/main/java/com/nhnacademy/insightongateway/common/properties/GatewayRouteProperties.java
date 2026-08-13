package com.nhnacademy.insightongateway.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway-route")
public record GatewayRouteProperties(String auth, String ai, String rule, String core) {
}
