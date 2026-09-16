package com.michael.chaos.web.xss;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对参数和请求头做基础 HTML 转义的请求包装器。
 *
 * <p>覆盖范围：{@link #getParameter}、{@link #getParameterValues}、{@link #getParameterMap}、
 * {@link #getHeader}、{@link #getHeaders}。原实现漏掉了 {@code getParameterMap()} 和 {@code getHeaders()}，
 * 而 Spring MVC 的 {@code @ModelAttribute} 数据绑定恰恰使用 {@code getParameterMap()}，导致防护形同虚设。</p>
 *
 * <p>局限：不处理 JSON/XML 请求体；输入端转义会改变入库数据（例如 {@code O'Brien}），
 * 因此默认关闭。推荐在输出端（模板引擎、前端框架）按上下文编码。</p>
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 这些请求头承载协议语义或认证凭据，转义会破坏其含义，保持原值。
     */
    private static final Set<String> RAW_HEADERS = Set.of(
            "authorization", "cookie", "content-type", "accept", "traceparent", "tracestate", "baggage"
    );

    /**
     * 创建 XSS 请求包装器。
     */
    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    /**
     * 返回已转义的单值请求参数。
     */
    @Override
    public String getParameter(String name) {
        return clean(super.getParameter(name));
    }

    /**
     * 返回已转义的多值请求参数。
     */
    @Override
    public String[] getParameterValues(String name) {
        return cleanAll(super.getParameterValues(name));
    }

    /**
     * 返回已转义的参数 Map，供 Spring 数据绑定使用。
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> original = super.getParameterMap();
        Map<String, String[]> cleaned = new LinkedHashMap<>(original.size());
        original.forEach((key, values) -> cleaned.put(key, cleanAll(values)));
        return Collections.unmodifiableMap(cleaned);
    }

    /**
     * 返回已转义的请求头。
     */
    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return isRawHeader(name) ? value : clean(value);
    }

    /**
     * 返回已转义的多值请求头。
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        Enumeration<String> values = super.getHeaders(name);
        if (values == null || isRawHeader(name)) {
            return values;
        }
        List<String> cleaned = Collections.list(values).stream().map(XssRequestWrapper::clean).toList();
        return Collections.enumeration(cleaned);
    }

    private static boolean isRawHeader(String name) {
        return name != null && RAW_HEADERS.contains(name.toLowerCase());
    }

    private static String[] cleanAll(String[] values) {
        if (values == null) {
            return null;
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = clean(values[i]);
        }
        return cleaned;
    }

    /**
     * 对常见 HTML 危险字符做实体转义。
     */
    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
