package com.michael.chaos.web.idempotent;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.core.idempotent.IdempotentRecord;
import com.michael.chaos.core.idempotent.IdempotentRejectedException;
import com.michael.chaos.core.idempotent.IdempotentRecordStore;
import com.michael.chaos.core.idempotent.IdempotentRepository;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.trace.TraceContext;
import com.michael.chaos.web.config.ChaosWebProperties;
import com.michael.chaos.web.i18n.ChaosMessageKeys;
import com.michael.chaos.web.i18n.ErrorMessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Web 请求幂等拦截器。
 *
 * <p>处理流程分三种状态：</p>
 * <ol>
 *     <li><b>已完成</b>：存在首次响应快照，直接回放并结束请求（仅在开启响应回放时）；</li>
 *     <li><b>执行中</b>：占位已被抢占但还没有快照，说明首次请求仍在处理，返回 409 让调用方稍后重试；</li>
 *     <li><b>未开始</b>：占位成功，放行给 Controller。</li>
 * </ol>
 *
 * <p>请求失败时释放占位，允许客户端使用同一个 key 重试；请求成功时保留占位直到 TTL 过期。</p>
 *
 * <p>只做"拒绝重复"时，客户端遇到网络超时重发只会拿到 409，它无法据此判断第一次究竟成功没有，
 * 只能再调一次查询接口对账。开启响应回放后，重复请求拿到的响应与第一次完全一致，
 * 支付、下单这类接口不必再为超时重试设计额外的对账链路。</p>
 */
public class IdempotentInterceptor implements HandlerInterceptor {

    private static final String IDEMPOTENT_KEY_ATTR = IdempotentInterceptor.class.getName() + ".KEY";

    /**
     * 请求成功且需要保存响应快照时写入的请求属性，由 {@link IdempotentResponseReplayFilter} 读取。
     */
    static final String REPLAY_CAPTURE_ATTR = IdempotentInterceptor.class.getName() + ".CAPTURE";

    private static final Logger log = LoggerFactory.getLogger(IdempotentInterceptor.class);

    private final IdempotentRepository repository;

    private final IdempotentKeyGenerator keyGenerator;

    private final ChaosWebProperties properties;

    private final IdempotentRecordStore recordStore;

    private final ErrorMessageResolver messageResolver;

    private final ChaosMetrics metrics;

    /**
     * 创建幂等拦截器，不启用响应回放。
     */
    public IdempotentInterceptor(
            IdempotentRepository repository,
            IdempotentKeyGenerator keyGenerator,
            ChaosWebProperties properties) {
        this(repository, keyGenerator, properties, null, null, null);
    }

    /**
     * 创建幂等拦截器，不上报指标。
     */
    public IdempotentInterceptor(
            IdempotentRepository repository,
            IdempotentKeyGenerator keyGenerator,
            ChaosWebProperties properties,
            IdempotentRecordStore recordStore,
            ErrorMessageResolver messageResolver) {
        this(repository, keyGenerator, properties, recordStore, messageResolver, null);
    }

    /**
     * 创建幂等拦截器。
     *
     * @param repository 幂等占位仓储
     * @param keyGenerator 幂等 key 生成器
     * @param properties Web 配置
     * @param recordStore 响应快照存储；为 {@code null} 时即使配置开启回放也只能退化为返回 409
     * @param messageResolver 文案解析器，可为 {@code null}
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public IdempotentInterceptor(
            IdempotentRepository repository,
            IdempotentKeyGenerator keyGenerator,
            ChaosWebProperties properties,
            IdempotentRecordStore recordStore,
            ErrorMessageResolver messageResolver,
            ChaosMetrics metrics) {
        this.repository = repository;
        this.keyGenerator = keyGenerator;
        this.properties = properties;
        this.recordStore = recordStore;
        this.messageResolver = messageResolver != null ? messageResolver : ErrorMessageResolver.none();
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 在 Controller 方法执行前校验幂等 key。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Idempotent annotation = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (annotation == null) {
            return true;
        }
        String key = resolveKey(annotation, request);
        if (key.isBlank()) {
            if (annotation.requireKey()) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, messageResolver.resolve(
                        ChaosMessageKeys.MISSING_IDEMPOTENCY_KEY,
                        "Missing or invalid " + ChaosHeaders.IDEMPOTENCY_KEY + " header",
                        ChaosHeaders.IDEMPOTENCY_KEY));
            }
            return true;
        }
        boolean replayEnabled = isReplayEnabled(annotation);
        // 先查快照再抢占位：占位与快照是两次写入，占位先过期时若只看占位就会重新执行业务，
        // 而快照其实还在。先查快照可以让"已完成"这个状态优先于"未开始"，避免重复执行。
        if (replayEnabled) {
            Optional<IdempotentRecord> record = findRecord(key);
            if (record.isPresent()) {
                replay(response, record.get());
                metrics.increment(ChaosMeterNames.IDEMPOTENT_REPLAYED);
                return false;
            }
        }
        if (!repository.saveIfAbsent(key, properties.getIdempotent().getTtl())) {
            metrics.increment(ChaosMeterNames.IDEMPOTENT_REJECTED);
            throw new IdempotentRejectedException();
        }
        request.setAttribute(IDEMPOTENT_KEY_ATTR, key);
        return true;
    }

    /**
     * 请求结束后判断是否需要释放幂等占位，或标记本次响应需要保存为快照。
     *
     * <p>不能只看 {@code ex != null}：业务异常已被 {@code GlobalExceptionHandler} 处理，DispatcherServlet
     * 传入的 ex 恒为 {@code null}，原实现导致请求失败后 key 永远不释放，客户端在 TTL 内重试一直收到 409。
     * 这里同时检查 DispatcherServlet 暴露的已处理异常属性和最终响应状态码（>= 400 视为失败）。</p>
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String key = (String) request.getAttribute(IDEMPOTENT_KEY_ATTR);
        if (key == null) {
            return;
        }
        if (isFailed(request, response, ex)) {
            repository.remove(key);
            return;
        }
        if (handler instanceof HandlerMethod handlerMethod) {
            Idempotent annotation = handlerMethod.getMethodAnnotation(Idempotent.class);
            if (annotation != null && isReplayEnabled(annotation)) {
                // 此时响应体还在 ContentCachingResponseWrapper 里没有回写，由外层过滤器负责读取并保存。
                request.setAttribute(REPLAY_CAPTURE_ATTR, key);
            }
        }
    }

    /**
     * 回放首次响应。
     */
    private void replay(HttpServletResponse response, IdempotentRecord record) {
        response.setStatus(record.statusCode());
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        record.headers().forEach(response::setHeader);
        response.setHeader(ChaosHeaders.IDEMPOTENCY_REPLAYED, "true");
        byte[] body = record.body();
        response.setContentLength(body.length);
        try {
            response.getOutputStream().write(body);
        } catch (IOException ioException) {
            throw new UncheckedIOException("Failed to replay idempotent response", ioException);
        }
    }

    /**
     * 查询响应快照。
     *
     * <p>快照存储不可用不应让正常请求失败：查询异常时按"没有快照"处理，请求走正常执行路径。
     * 这会牺牲一次回放，但比让整个接口 500 要好。</p>
     */
    private Optional<IdempotentRecord> findRecord(String key) {
        try {
            return recordStore.find(key);
        } catch (RuntimeException ex) {
            log.warn("Failed to read idempotent response record, falling back to normal execution, key={}", key, ex);
            return Optional.empty();
        }
    }

    private boolean isReplayEnabled(Idempotent annotation) {
        return annotation.replay() && properties.getIdempotent().getReplay().isEnabled() && recordStore != null;
    }

    private static boolean isFailed(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        return ex != null
                || request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) != null
                || response.getStatus() >= 400;
    }

    /**
     * 解析幂等 key：注解固定值优先，其次为请求头，并按租户、用户、方法和路径隔离。
     */
    private String resolveKey(Idempotent annotation, HttpServletRequest request) {
        String headerKey = request.getHeader(ChaosHeaders.IDEMPOTENCY_KEY);
        String key = keyGenerator.generate(new IdempotentKeyContext(
                annotation.key(),
                headerKey,
                request.getMethod(),
                request.getRequestURI(),
                TraceContext.traceId(),
                RequestContext.tenantId(),
                RequestContext.userId()
        ));
        return key == null ? "" : key;
    }
}
