package com.nhnacademy.insightongateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TokenBlacklistChecker {

    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenBlacklistChecker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isBlacklisted(String jti) {
        String key = RedisKey.BLACKLIST.getPrefix() + jti;
        return redisTemplate.hasKey(key);
    }
}
