package com.nhnacademy.insightongateway.auth;

import reactor.core.publisher.Mono;

public interface TokenBlacklistChecker {
    Mono<Boolean> isBlacklisted(String jtl);
 }
