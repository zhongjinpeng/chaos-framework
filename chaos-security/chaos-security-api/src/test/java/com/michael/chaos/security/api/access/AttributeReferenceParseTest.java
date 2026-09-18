package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 属性引用表达式解析测试。
 */
class AttributeReferenceParseTest {

    /**
     * 应识别三个命名空间前缀，env 是 environment 的简写。
     */
    @Test
    void shouldParseNamespacePrefix() {
        assertThat(AttributeReference.parse("subject.tenantId"))
                .isEqualTo(new AttributeReference(AttributeNamespace.SUBJECT, "tenantId"));
        assertThat(AttributeReference.parse("resource.ownerId"))
                .isEqualTo(new AttributeReference(AttributeNamespace.RESOURCE, "ownerId"));
        assertThat(AttributeReference.parse("environment.clientIp"))
                .isEqualTo(new AttributeReference(AttributeNamespace.ENVIRONMENT, "clientIp"));
        assertThat(AttributeReference.parse("ENV.clientIp"))
                .isEqualTo(new AttributeReference(AttributeNamespace.ENVIRONMENT, "clientIp"));
    }

    /**
     * 未知前缀整体作为环境属性名，便于书写 http.method 这类带点的属性。
     */
    @Test
    void shouldTreatUnknownPrefixAsEnvironmentAttributeName() {
        assertThat(AttributeReference.parse("http.method"))
                .isEqualTo(new AttributeReference(AttributeNamespace.ENVIRONMENT, "http.method"));
        assertThat(AttributeReference.parse("clientIp"))
                .isEqualTo(new AttributeReference(AttributeNamespace.ENVIRONMENT, "clientIp"));
    }

    /**
     * 嵌套属性名应保留剩余全部内容。
     */
    @Test
    void shouldKeepNestedAttributeName() {
        assertThat(AttributeReference.parse("environment.http.method"))
                .isEqualTo(new AttributeReference(AttributeNamespace.ENVIRONMENT, "http.method"));
    }

    /**
     * 空表达式应被拒绝。
     */
    @Test
    void shouldRejectBlankExpression() {
        assertThatThrownBy(() -> AttributeReference.parse(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttributeReference.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
