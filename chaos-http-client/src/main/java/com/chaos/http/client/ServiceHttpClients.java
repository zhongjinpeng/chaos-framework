package com.chaos.http.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 客户端工厂：按超时组合复用 {@link RestTemplate}，禁用重定向。
 *
 * <p>认证头由调用方按请求设置，客户端本身不持有凭据。</p>
 */
public final class ServiceHttpClients {

    private static final int MAX_CLIENTS = 32;

    private final RestTemplateBuilder builder;

    private final Map<String, RestTemplate> clients = new ConcurrentHashMap<>();

    /**
     * 注入 Spring Boot 的客户端构建器。
     *
     * @param builder 用于创建带自定义请求工厂的 {@link RestTemplate}
     */
    public ServiceHttpClients(RestTemplateBuilder builder) {
        this.builder = builder;
    }

    /**
     * 读取或创建指定超时组合的客户端。
     *
     * @param connectTimeoutMillis 连接超时毫秒数，必须为正数
     * @param readTimeoutMillis 读取超时毫秒数，必须为正数
     * @return 线程安全、禁止重定向的客户端
     */
    public RestTemplate forTimeouts(int connectTimeoutMillis, int readTimeoutMillis) {
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalArgumentException("HTTP 超时必须为正数");
        }
        String key = connectTimeoutMillis + "x" + readTimeoutMillis;
        return clients.computeIfAbsent(key, ignored -> create(connectTimeoutMillis, readTimeoutMillis));
    }

    private RestTemplate create(int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeoutMillis);
        factory.setReadTimeout(readTimeoutMillis);
        if (clients.size() >= MAX_CLIENTS) {
            clients.clear();
        }
        return builder.requestFactory(() -> factory).build();
    }
}
