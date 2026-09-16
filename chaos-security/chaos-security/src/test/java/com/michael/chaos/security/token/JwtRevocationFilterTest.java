package com.michael.chaos.security.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.config.ChaosSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * JWT 撤销检查过滤器测试。
 */
class JwtRevocationFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 已撤销 token 返回 401 JSON 错误体。
     */
    @Test
    void shouldWriteJsonBodyWhenRevoked() throws Exception {
        MockHttpServletResponse response = run(service(true, null), new ChaosSecurityProperties(), new AtomicBoolean());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"401\"");
        assertThat(response.getHeader("WWW-Authenticate")).contains("invalid_token");
    }

    /**
     * 撤销服务异常且未开启 fail-open 时返回 503，不能放行。
     */
    @Test
    void shouldFailClosedWhenRevocationServiceFails() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = run(
                service(false, new IllegalStateException("redis down")), new ChaosSecurityProperties(), invoked);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(invoked).isFalse();
    }

    /**
     * 显式开启 fail-open 时撤销服务异常应放行。
     */
    @Test
    void shouldFailOpenWhenConfigured() throws Exception {
        ChaosSecurityProperties properties = new ChaosSecurityProperties();
        properties.getJwt().setRevocationFailOpen(true);
        AtomicBoolean invoked = new AtomicBoolean();

        run(service(false, new IllegalStateException("redis down")), properties, invoked);

        assertThat(invoked).isTrue();
    }

    private MockHttpServletResponse run(
            JwtRevocationService service,
            ChaosSecurityProperties properties,
            AtomicBoolean invoked) throws Exception {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), Map.of("sub", "1"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtRevocationFilter(service, properties).doFilter(new MockHttpServletRequest(), response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse res) {
                        invoked.set(true);
                    }
                });
        return response;
    }

    private JwtRevocationService service(boolean revoked, RuntimeException failure) {
        return new JwtRevocationService() {
            @Override
            public boolean isRevoked(String tokenId) {
                if (failure != null) {
                    throw failure;
                }
                return revoked;
            }

            @Override
            public void revoke(String tokenId, Duration ttl) {
            }
        };
    }
}
