package com.nhnacademy.insightongateway.common;

import java.util.List;

public class SecurityConstants {

    private SecurityConstants() {
    }

    public static final List<String> EXCLUDED_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/check-email",
            "/api/v1/auth/email/**",
            "/api/v1/auth/refresh",
            "/api/v1/auth/find-email",
            "/api/v1/auth/password/**",
            "/api/v1/auth/reactivate/**",
            "/api/v1/auth/oauth/**",
            "/api/v1/admin/login",
            "/auth/v3/api-docs",
            "/core/v3/api-docs",
            "/ai/v3/api-docs",
            "/ruleengine/v3/api-docs",
            "/api/swagger",
            "/api/swagger/**",
            "/webjars/**"
    );
}
