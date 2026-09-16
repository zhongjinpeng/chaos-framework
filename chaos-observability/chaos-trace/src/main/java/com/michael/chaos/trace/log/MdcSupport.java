package com.michael.chaos.trace.log;

import java.util.Map;
import org.slf4j.MDC;

/**
 * MDC 操作辅助类。
 */
public final class MdcSupport {

    private MdcSupport() {
    }

    /**
     * 值非空时写入 MDC。
     */
    public static void putIfNotBlank(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    /**
     * 复制当前 MDC 上下文。
     */
    public static Map<String, String> copy() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return context == null ? Map.of() : Map.copyOf(context);
    }

    /**
     * 恢复 MDC 上下文。
     */
    public static void restore(Map<String, String> context) {
        MDC.clear();
        if (context != null && !context.isEmpty()) {
            MDC.setContextMap(context);
        }
    }

    /**
     * 清理框架维护的 MDC 字段。
     */
    public static void clearFrameworkKeys() {
        MDC.remove(MdcKeys.TRACE_ID);
        MDC.remove(MdcKeys.SPAN_ID);
        MDC.remove(MdcKeys.USER_ID);
        MDC.remove(MdcKeys.TENANT_ID);
        MDC.remove(MdcKeys.APP_NAME);
        MDC.remove(MdcKeys.URI);
        MDC.remove(MdcKeys.IP);
        MDC.remove(MdcKeys.COST);
    }
}
