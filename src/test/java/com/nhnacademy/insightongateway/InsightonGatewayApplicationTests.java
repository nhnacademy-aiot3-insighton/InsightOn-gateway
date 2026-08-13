package com.nhnacademy.insightongateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InsightonGatewayApplicationTests {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        registry.add("jwt.public-key-base64", () -> Base64.getEncoder().encodeToString(pem.getBytes()));
        registry.add("gateway-route.auth", () -> "http://localhost:8000");
        registry.add("gateway-route.ai", () -> "http://localhost:8100");
        registry.add("gateway-route.rule", () -> "http://localhost:8200");
        registry.add("gateway-route.core", () -> "http://localhost:8300");
    }

    @Test
    void contextLoads() {
    }
}
