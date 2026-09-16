package com.michael.chaos.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Gateway JWT 校验链测试。
 */
class GatewayJwtDecodersTest {

    /**
     * 配置 issuer 后签发方不一致的 token 必须被拒绝。
     */
    @Test
    void shouldRejectUnexpectedIssuer() {
        ChaosGatewayProperties.Jwt properties = new ChaosGatewayProperties.Jwt();
        properties.setIssuerUri("https://auth.example.com");
        OAuth2TokenValidator<Jwt> validator = GatewayJwtDecoders.validator(properties);

        assertThat(validator.validate(jwt("https://evil.example.com", List.of("orders"))).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("https://auth.example.com", List.of("orders"))).hasErrors()).isFalse();
    }

    /**
     * 配置 audiences 后 aud 不匹配的 token 必须被拒绝。
     */
    @Test
    void shouldRejectUnexpectedAudience() {
        ChaosGatewayProperties.Jwt properties = new ChaosGatewayProperties.Jwt();
        properties.setAudiences(List.of("gateway"));
        OAuth2TokenValidator<Jwt> validator = GatewayJwtDecoders.validator(properties);

        assertThat(validator.validate(jwt("https://auth.example.com", List.of("other-service"))).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("https://auth.example.com", List.of("gateway", "x"))).hasErrors()).isFalse();
    }

    /**
     * 过期 token 必须被拒绝。
     */
    @Test
    void shouldRejectExpiredToken() {
        OAuth2TokenValidator<Jwt> validator = GatewayJwtDecoders.validator(new ChaosGatewayProperties.Jwt());
        Jwt expired = new Jwt("t", Instant.now().minusSeconds(3600), Instant.now().minusSeconds(600),
                Map.of("alg", "RS256"), Map.of("sub", "1001"));

        assertThat(validator.validate(expired).hasErrors()).isTrue();
    }

    /**
     * 非法签名算法配置应在启动时失败。
     */
    @Test
    void shouldRejectUnsupportedAlgorithmConfiguration() {
        assertThatThrownBy(() -> GatewayJwtDecoders.signatureAlgorithms(List.of("none")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(GatewayJwtDecoders.signatureAlgorithms(List.of())).hasSize(1);
    }

    private Jwt jwt(String issuer, List<String> audiences) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(600),
                Map.of("alg", "RS256"),
                Map.of("sub", "1001", "iss", issuer, "aud", audiences));
    }
}
