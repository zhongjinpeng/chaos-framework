package com.michael.chaos.web.filter;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.ContextIdentifiers;
import com.michael.chaos.trace.log.MdcKeys;
import com.michael.chaos.trace.RequestTiming;
import com.michael.chaos.trace.RequestTimingContext;
import com.michael.chaos.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 访问日志过滤器。
 *
 * <p>记录请求方法、URI、响应状态和耗时，并将耗时写入 MDC 供 JSON 日志输出。</p>
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS_LOG");

    private static final long SLOW_REQUEST_NANOS = 1_000_000_000L;

    private static final List<String> EXCLUDED_PATHS =
            List.of("/actuator/**", "/metrics", "/metrics/**");

    private final String appName;

    private final String environment;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 创建请求访问和耗时日志过滤器。
     */
    public AccessLogFilter(String appName, String environment) {
        this.appName = appName;
        this.environment = environment;
    }

    /**
     * 计算请求耗时并输出访问日志。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RequestTiming timing = RequestTiming.start();
        boolean[] failed = {false};
        try (RequestTimingContext.Scope ignored = RequestTimingContext.open(timing)) {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error exception) {
            failed[0] = true;
            throw exception;
        } finally {
            RequestTiming.Snapshot snapshot = timing.snapshot();
            long costMs = Math.round(snapshot.totalMillis());
            MDC.put(MdcKeys.COST, Long.toString(costMs));
            logTiming(request, response, snapshot, failed[0]);
            MDC.remove(MdcKeys.COST);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void logTiming(
            HttpServletRequest request,
            HttpServletResponse response,
            RequestTiming.Snapshot snapshot,
            boolean executionFailed) {
        boolean failed = executionFailed || response.getStatus() >= 400;
        boolean slow = snapshot.totalNanos() >= SLOW_REQUEST_NANOS;
        String message = "event=request_timing env={} service={} protocol=http method={} path={} status={} "
                + "trace_id={} request_id={} total_ms={} stages={} counts={} slow={} slowest_stage={}";
        Object[] arguments = {
                environment,
                appName,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                TraceContext.traceId(),
                sanitizeRequestId(request.getHeader(ChaosHeaders.REQUEST_ID)),
                snapshot.totalMillis(),
                snapshot.stageMillis(),
                snapshot.stageCounts(),
                slow,
                snapshot.slowestStage()
        };
        if (failed || slow) {
            accessLog.warn(message, arguments);
        } else {
            accessLog.info(message, arguments);
        }
    }

    /**
     * 请求 ID 来自客户端，必须校验格式后再写日志，防止换行符伪造日志行。
     */
    private static String sanitizeRequestId(String requestId) {
        return ContextIdentifiers.sanitizeTraceId(requestId);
    }
}
