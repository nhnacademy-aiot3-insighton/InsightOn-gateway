package com.nhnacademy.insightongateway.config;

import com.nhnacademy.insightongateway.auth.JwtProperties;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyConfigTest {

    private final JwtKeyConfig jwtKeyConfig = new JwtKeyConfig();

    @Test
    void validPemKey_returnsMatchingRsaPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        String base64Pem = Base64.getEncoder().encodeToString(pem.getBytes());

        PublicKey result = jwtKeyConfig.jwtPublicKey(new JwtProperties(base64Pem));

        assertThat(result.getAlgorithm()).isEqualTo("RSA");
        assertThat(result.getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
    }

    @Test
    void nullKey_throwsIllegalStateException() {
        JwtProperties jwtProperties = new JwtProperties(null);
        assertThatThrownBy(() -> jwtKeyConfig.jwtPublicKey(jwtProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.public-key-base64 is null");
    }

    @Test
    void malformedBase64_throwsIllegalStateException() {
        JwtProperties jwtProperties = new JwtProperties("not-valid-base64!!!");
        assertThatThrownBy(() -> jwtKeyConfig.jwtPublicKey(jwtProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Jwt public key parsing failed");
    }

    @Test
    void validBase64ButNotAKey_throwsIllegalStateException() {
        String garbage = Base64.getEncoder().encodeToString("this is not a PEM key".getBytes());
        JwtProperties jwtProperties = new JwtProperties(garbage);
        assertThatThrownBy(() -> jwtKeyConfig.jwtPublicKey(jwtProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Jwt public key parsing failed");
    }
}
