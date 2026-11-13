package com.chaos.http.client;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * 固定服务地址的受限 HTTP 客户端。
 *
 * <p>统一处理服务令牌、幂等键、响应信封解包、连接失败重试一次和 401 刷新令牌；
 * 身份头、错误码翻译和响应上限由子类或构造参数决定，不绑定具体服务。</p>
 */
public class ServiceHttpClient {

    private static final Set<String> SUCCESS_CODES = Set.of("0", "200", "SUCCESS");

    private final String baseUrl;

    private final String caller;

    private final ServiceTokenProvider tokens;

    private final RestTemplate client;

    private final ObjectMapper json;

    private final int maxResponseBytes;

    private final ServiceErrorTranslator errorTranslator;

    /**
     * 创建固定服务地址的客户端。
     *
     * @param baseUrl 服务根地址
     * @param caller 调用方身份，供子类生成身份头与错误文案
     * @param tokens 服务令牌提供方
     * @param client 禁用重定向并设置超时的客户端
     * @param json 与服务端协议一致的序列化器
     * @param maxResponseBytes 允许的最大响应字节数
     * @param errorTranslator 下游错误码翻译器
     */
    protected ServiceHttpClient(
            String baseUrl,
            String caller,
            ServiceTokenProvider tokens,
            RestTemplate client,
            ObjectMapper json,
            int maxResponseBytes,
            ServiceErrorTranslator errorTranslator) {
        ServiceHttpAddress.resolve(baseUrl, "/");
        if (caller == null || caller.isBlank()) {
            throw new IllegalArgumentException("服务调用方身份不能为空");
        }
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("响应上限必须为正数");
        }
        this.baseUrl = baseUrl;
        this.caller = caller;
        this.tokens = tokens;
        this.client = client;
        this.maxResponseBytes = maxResponseBytes;
        this.errorTranslator = errorTranslator;
        this.json = json.copy();
        this.json.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(maxResponseBytes).build());
    }

    /**
     * 以服务身份调用固定路径，网络重试复用同一请求键。
     *
     * @param <T> 响应 data 的类型
     * @param path 以单个斜杠开始的服务路径
     * @param request 已由调用方完成业务授权的协议请求
     * @param type data 字段目标类型，保留泛型信息
     * @param requestKey 跨网络重试保持不变的幂等请求键
     * @param tenant 经调用方授权的资源实际归属租户
     * @param operator 操作人或系统身份
     * @return 解包后的 data，可为 {@code null}
     */
    public <T> T post(
            String path,
            Object request,
            ParameterizedTypeReference<T> type,
            String requestKey,
            String tenant,
            String operator) {
        return exchange(HttpMethod.POST, resolve(path), request, type, requestKey, tenant, operator);
    }

    /**
     * 以服务身份读取固定路径。
     *
     * @param <T> 响应 data 的类型
     * @param path 以单个斜杠开始的服务路径
     * @param type data 字段目标类型，保留泛型信息
     * @return 解包后的 data，可为 {@code null}
     */
    public <T> T get(String path, ParameterizedTypeReference<T> type) {
        return exchange(HttpMethod.GET, resolve(path), null, type, null, null, null);
    }

    /**
     * 以服务身份读取固定路径，并携带调用方身份头。
     *
     * @param <T> 响应 data 的类型
     * @param path 以单个斜杠开始的服务路径
     * @param type data 字段目标类型，保留泛型信息
     * @param tenant 经调用方授权的资源实际归属租户
     * @param operator 操作人或系统身份
     * @return 解包后的 data，可为 {@code null}
     */
    public <T> T get(
            String path, ParameterizedTypeReference<T> type, String tenant, String operator) {
        return exchange(HttpMethod.GET, resolve(path), null, type, null, tenant, operator);
    }

    /**
     * 以服务身份读取固定路径并附带查询参数。
     *
     * @param <T> 响应 data 的类型
     * @param path 以单个斜杠开始的服务路径，不含查询串
     * @param query 查询参数，按插入顺序编码
     * @param type data 字段目标类型，保留泛型信息
     * @return 解包后的 data，可为 {@code null}
     */
    public <T> T get(
            String path, Map<String, String> query, ParameterizedTypeReference<T> type) {
        return exchange(HttpMethod.GET, resolve(path, query), null, type, null, null, null);
    }

    private <T> T exchange(
            HttpMethod method,
            URI uri,
            Object request,
            ParameterizedTypeReference<T> type,
            String requestKey,
            String tenant,
            String operator) {
        HttpHeaders headers = new HttpHeaders();
        if (method == HttpMethod.POST) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", requestKey);
        }
        String token = tokens.token();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        addIdentityHeaders(headers, tenant, operator);
        for (int attempt = 0; ; attempt++) {
            try {
                byte[] response = client.execute(
                        uri,
                        method,
                        client.httpEntityCallback(new HttpEntity<>(request, headers)),
                        r -> com.chaos.core.io.BoundedStreams.read(r.getBody(), maxResponseBytes,
                                () -> new IllegalStateException("服务响应超过上限")));
                if (response == null) {
                    throw new IllegalStateException("服务响应超过上限");
                }
                var tree = json.readTree(response);
                if (tree != null && tree.has("code")) {
                    String code = tree.path("code").asText();
                    if (!SUCCESS_CODES.contains(code)) {
                        throw errorTranslator.translate(code, tree.path("message").asText("服务调用失败"));
                    }
                    tree = tree.get("data");
                }
                if (tree == null || tree.isNull()) {
                    return null;
                }
                return json.convertValue(tree, json.constructType(type.getType()));
            } catch (ResourceAccessException ex) {
                if (attempt >= 1) {
                    throw ex;
                }
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode().value() == 401 && attempt == 0) {
                    tokens.invalidate();
                    String refreshed = tokens.token();
                    if (refreshed != null && !refreshed.isBlank()) {
                        headers.setBearerAuth(refreshed);
                    } else {
                        headers.remove(HttpHeaders.AUTHORIZATION);
                    }
                    continue;
                }
                try {
                    var error = json.readTree(ex.getResponseBodyAsString(StandardCharsets.UTF_8));
                    throw errorTranslator.translate(
                            error.path("code").asText(), error.path("message").asText("服务调用失败"));
                } catch (IOException malformed) {
                    throw ex;
                }
            } catch (IOException ex) {
                throw new IllegalStateException("服务响应格式错误", ex);
            }
        }
    }

    private URI resolve(String path) {
        return ServiceHttpAddress.resolve(baseUrl, path);
    }

    private URI resolve(String path, Map<String, String> query) {
        URI uri = resolve(path);
        if (query == null || query.isEmpty()) {
            return uri;
        }
        var builder = new StringBuilder(uri.toString()).append('?');
        boolean first = true;
        for (var entry : query.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(builder.toString());
    }

    /**
     * 添加调用方身份头；默认不添加，子类按自身协议覆盖。
     *
     * @param headers 即将发送的请求头
     * @param tenant 资源实际归属租户
     * @param operator 操作人或系统身份
     */
    protected void addIdentityHeaders(HttpHeaders headers, String tenant, String operator) {
    }

    /**
     * 调用方身份，供子类生成身份头或错误文案。
     *
     * @return 构造时传入的调用方名称
     */
    protected String caller() {
        return caller;
    }
}
