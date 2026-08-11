package com.nhnacademy.insightongateway.auth;

import com.nhnacademy.insightongateway.common.RedisKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.time.Duration;

@Testcontainers
class RedisTokenBlacklistCheckerTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("bitnamilegacy/redis:7.4.3"))
            .withEnv("REDIS_PASSWORD", "testpassword")
            .withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static ReactiveStringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
        config.setPassword("testpassword");
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @Test
    void isBlacklisted() {
        RedisTokenBlacklistChecker checker = new RedisTokenBlacklistChecker(redisTemplate);
        String jti = "real-jti-999";
        String key = RedisKey.BLACKLIST.getPrefix() + jti;
        redisTemplate.opsForValue().set(key, "revoked", Duration.ofMinutes(5)).block();
        StepVerifier.create(checker.isBlacklisted(jti))
                .expectNext(true)
                .verifyComplete();
    }
    /*
    LettuceConnectionFactory는 Spring Data Redis가 실제 Redis 서버와 연결을 만들 때 사용하는 컨넥션 팩토리 구현체
    - Redis용 java 클라이언트 라이브러리 중 하나
    - Netty 기반 비동기/리액티브 클라이언트라서 ReactiveStringRedisTemplate 처럼 Reactive API를 사용하려면 반드시 Lettuce가 필요함
     */
}