package com.chaos.http.client;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 使用 OAuth2 client_credentials 获取短期服务令牌；凭据只发往固定授权服务。
 */
public final class ClientCredentialsTokenProvider implements ServiceTokenProvider {

    private static final int REFRESH_AHEAD_SECONDS = 30;

    private final URI tokenUri;

    private final String clientId;

    private final String secret;

    private final String scope;

    private final RestTemplate http;

    private String token;

    private Instant expires = Instant.EPOCH;

    /**
     * 创建只向固定授权服务发送凭据的令牌提供方。
     *
     * @param authBase 固定 OAuth2 授权服务地址
     * @param clientId 已注册的服务客户端标识
     * @param secret 环境提供的服务密钥，不得记录日志
     * @param scope 该客户端申请的最小 OAuth2 scope
     * @param clients 禁用重定向并设置超时的 HTTP 工厂
     */
    public ClientCredentialsTokenProvider(
            String authBase, String clientId, String secret, String scope, ServiceHttpClients clients) {
        this.tokenUri = ServiceHttpAddress.resolve(authBase, "/oauth2/token");
        if (clientId == null || clientId.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("服务 OAuth2 凭据必须配置");
        }
        this.clientId = clientId;
        this.secret = secret;
        this.scope = scope;
        this.http = clients.forTimeouts(2500, 5000);
    }

    @Override
    public synchronized String token() {
        if (token != null && Instant.now().isBefore(expires)) {
            return token;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, secret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);
        Map<?, ?> response = http.postForObject(tokenUri, new HttpEntity<>(form, headers), Map.class);
        if (response == null
                || !(response.get("access_token") instanceof String value)
                || value.isBlank()
                || !(response.get("expires_in") instanceof Number duration)
                || duration.longValue() <= 0) {
            throw new IllegalStateException("授权服务未返回有效服务令牌");
        }
        token = value;
        expires = Instant.now().plusSeconds(Math.max(1, duration.longValue() - REFRESH_AHEAD_SECONDS));
        return token;
    }

    @Override
    public synchronized void invalidate() {
        token = null;
        expires = Instant.EPOCH;
    }
}
