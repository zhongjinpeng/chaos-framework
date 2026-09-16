package com.michael.chaos.security.api.datascope;

import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.access.AuthorizationRequest;
import com.michael.chaos.security.api.access.AuthorizationResource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 数据权限决策请求。
 *
 * @param scope 数据权限策略编码
 * @param subject 访问主体
 * @param mappedStatementId MyBatis mapped statement ID
 * @param tableName 当前 SQL 表名
 * @param environment 环境属性
 */
public record DataScopeRequest(
        String scope,
        AccessSubject subject,
        String mappedStatementId,
        String tableName,
        Map<String, Object> environment
) {

    /**
     * 规范化数据权限请求字段。
     */
    public DataScopeRequest {
        scope = Objects.requireNonNullElse(scope, "").trim();
        subject = subject == null ? AccessSubject.ANONYMOUS : subject;
        mappedStatementId = Objects.requireNonNullElse(mappedStatementId, "").trim();
        tableName = Objects.requireNonNullElse(tableName, "").trim();
        environment = copyAttributes(environment);
    }

    /**
     * 创建只有策略编码的数据权限请求。
     */
    public static DataScopeRequest of(String scope) {
        return new DataScopeRequest(scope, AccessSubject.ANONYMOUS, "", "", Map.of());
    }

    /**
     * 创建包含访问主体的数据权限请求。
     */
    public static DataScopeRequest of(String scope, AccessSubject subject) {
        return new DataScopeRequest(scope, subject, "", "", Map.of());
    }

    /**
     * 返回带 SQL 元信息的新请求。
     */
    public DataScopeRequest withSql(String mappedStatementId, String tableName) {
        return new DataScopeRequest(scope, subject, mappedStatementId, tableName, environment);
    }

    /**
     * 转换为通用授权请求，便于复用 ABAC/RBAC 决策模型。
     */
    public AuthorizationRequest toAuthorizationRequest(String action) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("scope", scope);
        attributes.put("mappedStatementId", mappedStatementId);
        return AuthorizationRequest.of(
                subject,
                action,
                AuthorizationResource.of("data-scope", tableName, attributes),
                environment
        );
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = Objects.requireNonNullElse(entry.getKey(), "").trim();
            if (!key.isBlank()) {
                copied.put(key, entry.getValue());
            }
        }
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }
}
