package com.nhnacademy.insightongateway.config;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.PublicKey;

@Configuration
public class JwtParserConfig {

    @Bean
    public JwtParser jwtParser(PublicKey jwtPublicKey) {
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build();
    }
}
