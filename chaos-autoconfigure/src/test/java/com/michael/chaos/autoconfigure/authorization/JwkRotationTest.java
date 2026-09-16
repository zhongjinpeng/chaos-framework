package com.michael.chaos.autoconfigure.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWK 轮换测试。
 */
class JwkRotationTest {

    @TempDir
    Path tempDir;

    /**
     * 配置旧公钥后：新密钥仍可正常签发（不会因多把密钥匹配而失败），旧密钥签发的在途 token 仍可验签。
     */
    @Test
    void shouldSignWithCurrentKeyAndVerifyTokensSignedByPreviousKey() throws Exception {
        KeyPair current = generate();
        KeyPair previous = generate();
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        properties.getJwk().setKeyId("current");
        properties.getJwk().setPublicKeyLocation(writePem("current-public.pem", "PUBLIC KEY", current.getPublic().getEncoded()));
        properties.getJwk().setPrivateKeyLocation(writePem("current-private.pem", "PRIVATE KEY", current.getPrivate().getEncoded()));
        ChaosAuthorizationProperties.PreviousKey previousKey = new ChaosAuthorizationProperties.PreviousKey();
        previousKey.setKeyId("previous");
        previousKey.setPublicKeyLocation(writePem("previous-public.pem", "PUBLIC KEY", previous.getPublic().getEncoded()));
        properties.getJwk().setPreviousPublicKeys(List.of(previousKey));
        ChaosAuthorizationAutoConfiguration configuration = new ChaosAuthorizationAutoConfiguration();

        JWKSource<SecurityContext> jwkSource = configuration.jwkSource(properties);
        JwtDecoder decoder = configuration.jwtDecoder(jwkSource);

        String currentToken = new NimbusJwtEncoder(ChaosAuthorizationAutoConfiguration.signingKeySource(jwkSource))
                .encode(parameters(null)).getTokenValue();
        RSAKey previousPrivate = new RSAKey.Builder((RSAPublicKey) previous.getPublic())
                .privateKey((RSAPrivateKey) previous.getPrivate())
                .keyID("previous")
                .build();
        String previousToken = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(previousPrivate)))
                .encode(parameters("previous")).getTokenValue();

        assertThat(decoder.decode(currentToken).getSubject()).isEqualTo("1001");
        assertThat(decoder.decode(previousToken).getSubject()).isEqualTo("1001");
    }

    private JwtEncoderParameters parameters(String keyId) {
        JwsHeader.Builder header = JwsHeader.with(SignatureAlgorithm.RS256);
        if (keyId != null) {
            header.keyId(keyId);
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        return JwtEncoderParameters.from(header.build(), claims);
    }

    private KeyPair generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String writePem(String name, String type, byte[] der) throws Exception {
        Path file = tempDir.resolve(name);
        String pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
        Files.writeString(file, pem);
        return file.toString();
    }
}
