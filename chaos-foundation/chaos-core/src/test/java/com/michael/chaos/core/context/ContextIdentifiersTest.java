package com.michael.chaos.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 上下文标识符白名单测试。
 */
class ContextIdentifiersTest {

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    /**
     * 常见租户/用户 ID 格式应被接受。
     */
    @Test
    void shouldAcceptCommonIdentifierFormats() {
        assertTrue(ContextIdentifiers.isValidIdentifier("tenant-a"));
        assertTrue(ContextIdentifiers.isValidIdentifier("10001"));
        assertTrue(ContextIdentifiers.isValidIdentifier("user@example.com"));
        assertTrue(ContextIdentifiers.isValidIdentifier("org:dept_1.team"));
    }

    /**
     * 包含引号、空白、换行或超长的值必须被拒绝，防止 SQL 注入和日志/响应头注入。
     */
    @Test
    void shouldRejectDangerousIdentifiers() {
        assertFalse(ContextIdentifiers.isValidIdentifier("x' OR '1'='1"));
        assertFalse(ContextIdentifiers.isValidIdentifier("tenant\r\nX-Evil: 1"));
        assertFalse(ContextIdentifiers.isValidIdentifier("a b"));
        assertFalse(ContextIdentifiers.isValidIdentifier("租户"));
        assertFalse(ContextIdentifiers.isValidIdentifier("a".repeat(129)));
        assertFalse(ContextIdentifiers.isValidIdentifier(""));
        assertFalse(ContextIdentifiers.isValidIdentifier(null));
    }

    /**
     * 快照构造时应把非法租户/用户 ID 规范化为空字符串，任何入口都无法绕过。
     */
    @Test
    void snapshotShouldDropIllegalTenantAndUser() {
        RequestContextSnapshot snapshot = new RequestContextSnapshot("t", "s", "x' OR '1'='1", "u\n1", "app");

        assertEquals("", snapshot.tenantId());
        assertEquals("", snapshot.userId());

        RequestContext.setTenantId("tenant'; drop table t;--");
        assertEquals("", RequestContext.tenantId());
        RequestContext.setTenantId(" tenant-b ");
        assertEquals("tenant-b", RequestContext.tenantId());
    }

    /**
     * 兼容旧系统的 traceId 只接受字母数字、下划线和中划线。
     */
    @Test
    void shouldSanitizeLegacyTraceId() {
        assertEquals("trace-main_1", ContextIdentifiers.sanitizeTraceId(" trace-main_1 "));
        assertEquals("", ContextIdentifiers.sanitizeTraceId("trace\r\nSet-Cookie: a=b"));
        assertEquals("", ContextIdentifiers.sanitizeTraceId("x".repeat(65)));
    }
}
