package com.nhnacademy.insightongateway.common;

import java.util.List;

public class SecurityConstants {

    private SecurityConstants() {}

    public static final List<String> EXCLUDED_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/user/oauth/**"
    );
}
