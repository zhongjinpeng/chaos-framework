package com.michael.chaos.web.idempotent;

import com.michael.chaos.core.idempotent.IdempotentRecord;
import com.michael.chaos.core.idempotent.IdempotentRecordStore;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.web.config.ChaosWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 幂等响应快照采集过滤器。
 *
 * <p>{@code HandlerInterceptor} 拿不到响应体：{@code afterCompletion} 执行时消息转换器已经把 JSON 写进了
 * 响应输出流，没有任何 API 能再读回来。因此采集必须放在过滤器里，用
 * {@link ContentCachingResponseWrapper} 把响应体缓存下来，等拦截器在 {@code afterCompletion} 里
 * 确认本次请求成功后，再由本过滤器读取缓存并写入 {@link IdempotentRecordStore}。</p>
 *
 * <p>包装范围：只包装可能触发幂等的写请求（{@code POST}、{@code PUT}、{@code PATCH}、{@code DELETE}）。
 * 查询请求天然幂等、且响应通常比写请求大得多，包装它们只会白白占用内存。
 * 单次响应体超过 {@code chaos.web.idempotent.replay.max-body-size} 时放弃保存快照，
 * 该 key 的重复请求会退回 409，这比让一个大响应撑爆 Redis 或堆内存更安全。</p>
 */
public class IdempotentResponseReplayFilter extends OncePerRequestFilter {

    /**
     * 可能改变服务端状态、因而需要幂等保护的请求方法。
     */
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final Logger log = LoggerFactory.getLogger(IdempotentResponseReplayFilter.class);

    private final IdempotentRecordStore recordStore;

    private final ChaosWebProperties properties;

    private final ChaosMetrics metrics;

    /**
     * 创建响应快照采集过滤器，不上报指标。
     */
    public IdempotentResponseReplayFilter(IdempotentRecordStore recordStore, ChaosWebProperties properties) {
        this(recordStore, properties, null);
    }

    /**
     * 创建响应快照采集过滤器。
     *
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public IdempotentResponseReplayFilter(
            IdempotentRecordStore recordStore, ChaosWebProperties properties, ChaosMetrics metrics) {
        this.recordStore = recordStore;
        this.properties = properties;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 只在开启响应回放且请求方法可能触发幂等时才包装响应。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getIdempotent().getReplay().isEnabled()
                || !MUTATING_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
            saveRecordIfRequested(request, wrapper);
        } finally {
            // 无论是否保存快照，都必须把缓存的响应体回写给真实响应，否则客户端收到空响应。
            wrapper.copyBodyToResponse();
        }
    }

    /**
     * 拦截器确认请求成功后才会写入采集标记，这里只负责读取缓存并保存。
     */
    private void saveRecordIfRequested(HttpServletRequest request, ContentCachingResponseWrapper wrapper) {
        Object key = request.getAttribute(IdempotentInterceptor.REPLAY_CAPTURE_ATTR);
        if (!(key instanceof String idempotentKey)) {
            return;
        }
        if (request.isAsyncStarted()) {
            // 异步请求此刻还没产生完整响应体，保存下来的快照会是空的，比不保存更糟。
            log.debug("Skip idempotent response record for async request, key={}", idempotentKey);
            metrics.increment(ChaosMeterNames.IDEMPOTENT_RECORD_SKIPPED, ChaosMeterNames.TAG_REASON, "async");
            return;
        }
        long maxBodySize = properties.getIdempotent().getReplay().getMaxBodySize().toBytes();
        if (wrapper.getContentSize() > maxBodySize) {
            log.info("Response body exceeds chaos.web.idempotent.replay.max-body-size ({} > {} bytes), "
                    + "duplicate requests will be rejected with 409 instead of replayed, key={}",
                    wrapper.getContentSize(), maxBodySize, idempotentKey);
            metrics.increment(ChaosMeterNames.IDEMPOTENT_RECORD_SKIPPED, ChaosMeterNames.TAG_REASON, "too-large");
            return;
        }
        IdempotentRecord record = new IdempotentRecord(
                wrapper.getStatus(),
                wrapper.getContentType(),
                wrapper.getContentAsByteArray(),
                storedHeaders(wrapper),
                Instant.now().toEpochMilli());
        try {
            recordStore.save(idempotentKey, record, properties.getIdempotent().getTtl());
        } catch (RuntimeException ex) {
            // 快照写入失败只影响后续能否回放，不应把已经成功的业务响应变成 500。
            log.warn("Failed to save idempotent response record, key={}", idempotentKey, ex);
            metrics.increment(ChaosMeterNames.IDEMPOTENT_RECORD_SKIPPED, ChaosMeterNames.TAG_REASON, "error");
        }
    }

    /**
     * 只保留配置指定的响应头。
     *
     * <p>默认是 {@code Location}：创建类接口的资源地址在响应头里，不一并回放的话，回放响应与首次响应不等价。
     * 其余响应头（{@code Date}、trace、鉴权相关）都应由本次请求重新生成，回放旧值反而会产生误导。</p>
     */
    private Map<String, String> storedHeaders(ContentCachingResponseWrapper wrapper) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : properties.getIdempotent().getReplay().getStoredHeaders()) {
            String value = wrapper.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }
}
