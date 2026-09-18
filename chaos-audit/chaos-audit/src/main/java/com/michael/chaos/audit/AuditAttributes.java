package com.michael.chaos.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计事件属性构造器。
 *
 * <p>审计属性几乎总是"有值才写"——登录没带设备号、会话没有 User-Agent 时，写一个空字符串只会污染审计表。
 * 此前每个发布审计事件的类都自己写一份 {@code putIfNotBlank}（授权服务器、互踢、Redis 互踢、刷新令牌四处
 * 完全相同），现在统一到这里。</p>
 *
 * <p>属性按写入顺序保留，便于日志与审计表里字段顺序稳定。</p>
 */
public final class AuditAttributes {

    private final Map<String, String> attributes = new LinkedHashMap<>();

    private AuditAttributes() {
    }

    /**
     * 创建构造器。
     */
    public static AuditAttributes create() {
        return new AuditAttributes();
    }

    /**
     * 写入属性；键或值为空白时忽略。
     */
    public AuditAttributes putIfNotBlank(String key, String value) {
        if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
            attributes.put(key.trim(), value.trim());
        }
        return this;
    }

    /**
     * 写入属性；值为 null 时忽略，空字符串会被写入。
     *
     * <p>布尔、数量这类"值本身就是结论"的属性用这个方法，不要用 {@link #putIfNotBlank}，
     * 否则 {@code false} 之外的空值会被悄悄丢掉。</p>
     */
    public AuditAttributes put(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            attributes.put(key.trim(), value);
        }
        return this;
    }

    /**
     * 写入布尔属性。
     */
    public AuditAttributes put(String key, boolean value) {
        return put(key, Boolean.toString(value));
    }

    /**
     * 构造不可变属性 Map。
     */
    public Map<String, String> build() {
        return Map.copyOf(attributes);
    }
}
