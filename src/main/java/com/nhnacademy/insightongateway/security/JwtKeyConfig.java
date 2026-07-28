package com.nhnacademy.insightongateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    @Bean
    public PublicKey jwtPublicKey(JwtProperties jwtProperties) {
        String base64Pem = jwtProperties.getPublicKeyBase64();
        if (Objects.isNull(base64Pem)) {
            throw new IllegalStateException("[JwtKeyConfig] jwt.public-key-base64 is null");
        }
        try {
            byte[] pemBytes = Base64.getDecoder().decode(jwtProperties.getPublicKeyBase64());
            String pemText = new String(pemBytes, StandardCharsets.UTF_8);

            String keyBody = pemText
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(keyBody);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalStateException("[JwtKeyConfig] Jwt public key parsing failed", e);
        }
    }
}
