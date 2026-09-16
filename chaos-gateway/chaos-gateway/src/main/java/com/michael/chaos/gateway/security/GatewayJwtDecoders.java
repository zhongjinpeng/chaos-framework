package com.michael.chaos.gateway.security;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

/**
 * Gateway JWT 解码器工厂。
 *
 * <p>{@code NimbusReactiveJwtDecoder.withJwkSetUri(...).build()} 默认只校验 exp/nbf，
 * 不校验签发方和受众：同一个 JWKS 为其他客户端、其他受众签发的 token 也能通过网关。
 * 这里统一补齐 iss、aud 与签名算法白名单校验。</p>
 */
public final class GatewayJwtDecoders {

    private GatewayJwtDecoders() {
    }

    /**
     * 按网关配置创建带完整校验链的响应式 JWT 解码器。
     */
    public static NimbusReactiveJwtDecoder create(ChaosGatewayProperties.Jwt jwt) {
        List<SignatureAlgorithm> algorithms = signatureAlgorithms(jwt.getJwsAlgorithms());
        NimbusReactiveJwtDecoder.JwkSetUriReactiveJwtDecoderBuilder builder =
                NimbusReactiveJwtDecoder.withJwkSetUri(jwt.getJwkSetUri());
        algorithms.forEach(builder::jwsAlgorithm);
        NimbusReactiveJwtDecoder decoder = builder.build();
        decoder.setJwtValidator(validator(jwt));
        return decoder;
    }

    /**
     * 解析签名算法白名单；未知算法（包括 {@code none}）直接启动失败。
     */
    static List<SignatureAlgorithm> signatureAlgorithms(List<String> configured) {
        List<String> names = configured == null || configured.isEmpty() ? List.of("RS256") : configured;
        List<SignatureAlgorithm> algorithms = new ArrayList<>();
        for (String name : names) {
            SignatureAlgorithm algorithm = name == null ? null : SignatureAlgorithm.from(name.trim());
            if (algorithm == null) {
                throw new IllegalStateException("Unsupported chaos.gateway.jwt.jws-algorithms value: " + name);
            }
            algorithms.add(algorithm);
        }
        return algorithms;
    }

    /**
     * 构建 JWT claim 校验器：时间窗口必选，签发方和受众在配置后强制校验。
     */
    public static OAuth2TokenValidator<Jwt> validator(ChaosGatewayProperties.Jwt jwt) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        Duration clockSkew = jwt.getClockSkew() == null ? Duration.ofSeconds(60) : jwt.getClockSkew();
        validators.add(new JwtTimestampValidator(clockSkew));
        if (jwt.getIssuerUri() != null && !jwt.getIssuerUri().isBlank()) {
            validators.add(new JwtIssuerValidator(jwt.getIssuerUri().trim()));
        }
        List<String> audiences = jwt.getAudiences().stream()
                .filter(audience -> audience != null && !audience.isBlank())
                .map(String::trim)
                .toList();
        if (!audiences.isEmpty()) {
            validators.add(new JwtClaimValidator<Collection<String>>(
                    JwtClaimNames.AUD,
                    tokenAudiences -> tokenAudiences != null && tokenAudiences.stream().anyMatch(audiences::contains)
            ));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
