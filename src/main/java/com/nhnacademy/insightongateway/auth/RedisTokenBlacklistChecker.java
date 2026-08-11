package com.nhnacademy.insightongateway.auth;

import com.nhnacademy.insightongateway.common.RedisKey;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisTokenBlacklistChecker implements TokenBlacklistChecker {

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisTokenBlacklistChecker(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Boolean> isBlacklisted(String jti) {
        String key = RedisKey.BLACKLIST.getPrefix() + jti;
        return redisTemplate.hasKey(key);
    }
}
