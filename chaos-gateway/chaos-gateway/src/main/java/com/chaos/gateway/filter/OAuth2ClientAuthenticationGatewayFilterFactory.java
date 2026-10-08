package com.chaos.gateway.filter;

import com.chaos.core.diagnostic.ChaosDiagnostic;
import com.chaos.core.diagnostic.ChaosDiagnosticException;
import com.chaos.gateway.config.ChaosGatewayProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * 按注册名称为 OAuth2 token 代理路由注入保密客户端的 Basic 凭据。
 *
 * <p>调用方携带的 Authorization 头会被无条件替换，客户端密钥只保留在 Gateway 服务端。</p>
 */
public class OAuth2ClientAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<OAuth2ClientAuthenticationGatewayFilterFactory.Config> {

    private final ChaosGatewayProperties properties;

    public OAuth2ClientAuthenticationGatewayFilterFactory(ChaosGatewayProperties properties) {
        super(Config.class);
        this.properties = properties;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("registration");
    }

    @Override
    public GatewayFilter apply(Config config) {
        String registration = config == null ? null : config.getRegistration();
        ChaosGatewayProperties.OAuth2Client client = resolveClient(registration);
        String authorizationValue = basicAuthorization(client);
        return (exchange, chain) -> chain.filter(withClientAuthorization(exchange, authorizationValue));
    }

    private ChaosGatewayProperties.OAuth2Client resolveClient(String registration) {
        if (registration == null || registration.isBlank()) {
            throw configurationException(
                    "OAuth2 客户端认证过滤器缺少注册名称",
                    "路由配置了 OAuth2ClientAuthentication，但没有指定客户端注册名称",
                    "使用 OAuth2ClientAuthentication=<registration>，并配置 chaos.gateway.oauth2-clients.<registration>");
        }
        ChaosGatewayProperties.OAuth2Client client = properties.getOauth2Clients().get(registration);
        if (client == null) {
            throw configurationException(
                    "OAuth2 客户端注册不存在",
                    "路由引用了未配置的 OAuth2 客户端注册：" + registration,
                    "配置 chaos.gateway.oauth2-clients." + registration + ".client-id/client-secret");
        }
        if (client.getClientId() == null || client.getClientId().isBlank()
                || client.getClientSecret() == null || client.getClientSecret().isBlank()) {
            throw configurationException(
                    "OAuth2 客户端注册不完整",
                    "OAuth2 客户端注册 " + registration + " 的 client-id 或 client-secret 为空",
                    "配置 chaos.gateway.oauth2-clients." + registration + ".client-id/client-secret");
        }
        return client;
    }

    private static String basicAuthorization(ChaosGatewayProperties.OAuth2Client client) {
        String credentials = formEncode(client.getClientId()) + ":" + formEncode(client.getClientSecret());
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ServerWebExchange withClientAuthorization(
            ServerWebExchange exchange,
            String authorizationValue) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.set(HttpHeaders.AUTHORIZATION, authorizationValue);
                }))
                .build();
    }

    private static ChaosDiagnosticException configurationException(
            String problem,
            String cause,
            String fix) {
        return new ChaosDiagnosticException(ChaosDiagnostic.of(problem, cause, fix));
    }

    /**
     * 路由过滤器参数。
     */
    public static class Config {

        private String registration;

        public String getRegistration() {
            return registration;
        }

        public void setRegistration(String registration) {
            this.registration = registration;
        }
    }
}
