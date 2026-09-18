package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ABAC 属性条件新增操作符测试。
 */
class AttributeConditionOperatorsTest {

    /**
     * 数值属性应按数值大小比较，而不是字符串比较。
     */
    @Test
    void shouldCompareNumbersNumerically() {
        AuthorizationRequest request = request(Map.of("amount", 1000));

        assertThat(AttributeCondition.gt(AttributeReference.resource("amount"), "999").matches(request)).isTrue();
        assertThat(AttributeCondition.gt(AttributeReference.resource("amount"), "1000").matches(request)).isFalse();
        assertThat(AttributeCondition.gte(AttributeReference.resource("amount"), "1000").matches(request)).isTrue();
        assertThat(AttributeCondition.lt(AttributeReference.resource("amount"), "1001").matches(request)).isTrue();
        assertThat(AttributeCondition.lte(AttributeReference.resource("amount"), "999").matches(request)).isFalse();
    }

    /**
     * 时间属性应按时间比较。
     */
    @Test
    void shouldCompareTimeValues() {
        AuthorizationRequest request = request(Map.of("now", "2026-09-18T10:15:30Z"));
        AttributeReference now = AttributeReference.resource("now");

        assertThat(AttributeCondition.gt(now, "2026-09-18T09:00:00Z").matches(request)).isTrue();
        assertThat(AttributeCondition.lt(now, "2026-09-18T09:00:00Z").matches(request)).isFalse();
    }

    /**
     * 闭区间判断与两个边界值的先后顺序无关。
     */
    @Test
    void betweenShouldBeInclusiveAndOrderIndependent() {
        AuthorizationRequest request = request(Map.of("hour", "12:30"));
        AttributeReference hour = AttributeReference.resource("hour");

        assertThat(AttributeCondition.between(hour, "09:00", "18:00").matches(request)).isTrue();
        assertThat(AttributeCondition.between(hour, "18:00", "09:00").matches(request)).isTrue();
        assertThat(AttributeCondition.between(hour, "12:30", "18:00").matches(request)).isTrue();
        assertThat(AttributeCondition.between(hour, "13:00", "18:00").matches(request)).isFalse();
    }

    /**
     * 正则应做完整匹配，非法正则返回 false。
     */
    @Test
    void regexShouldMatchWholeValue() {
        AuthorizationRequest request = request(Map.of("ip", "192.168.1.7"));
        AttributeReference ip = AttributeReference.resource("ip");

        assertThat(AttributeCondition.regex(ip, "192\\.168\\..*").matches(request)).isTrue();
        assertThat(AttributeCondition.regex(ip, "10\\..*").matches(request)).isFalse();
        assertThat(AttributeCondition.regex(ip, "192\\.168").matches(request)).isFalse();
        assertThat(AttributeCondition.regex(ip, "[").matches(request)).isFalse();
    }

    /**
     * CONTAINS 要求集合包含全部指定值、字符串包含全部子串。
     */
    @Test
    void containsShouldRequireAllValues() {
        AuthorizationRequest collectionRequest = request(Map.of("tags", List.of("vip", "beta", "cn")));
        AttributeReference tags = AttributeReference.resource("tags");

        assertThat(AttributeCondition.contains(tags, Set.of("vip")).matches(collectionRequest)).isTrue();
        assertThat(AttributeCondition.contains(tags, Set.of("vip", "beta")).matches(collectionRequest)).isTrue();
        assertThat(AttributeCondition.contains(tags, Set.of("vip", "gold")).matches(collectionRequest)).isFalse();

        AuthorizationRequest textRequest = request(Map.of("path", "/api/order/1"));
        assertThat(AttributeCondition.contains(AttributeReference.resource("path"), Set.of("/order/"))
                .matches(textRequest)).isTrue();
    }

    /**
     * 类型不可比较或属性缺失时不应命中，也不应抛异常。
     */
    @Test
    void shouldNotMatchWhenValueIsMissingOrIncomparable() {
        AuthorizationRequest request = request(Map.of("amount", "abc"));
        AttributeReference amount = AttributeReference.resource("amount");
        AttributeReference missing = AttributeReference.resource("missing");

        assertThat(AttributeCondition.gt(amount, "100").matches(request)).isFalse();
        assertThat(AttributeCondition.gt(missing, "100").matches(request)).isFalse();
        assertThat(AttributeCondition.between(missing, "1", "2").matches(request)).isFalse();
        assertThat(AttributeCondition.regex(missing, ".*").matches(request)).isFalse();
        assertThat(AttributeCondition.contains(missing, Set.of("x")).matches(request)).isFalse();
    }

    /**
     * 字符串属性在无法解析为数值或时间时按字典序比较。
     */
    @Test
    void shouldFallBackToStringComparison() {
        AuthorizationRequest request = request(Map.of("level", "b"));

        assertThat(AttributeCondition.gt(AttributeReference.resource("level"), "a").matches(request)).isTrue();
        assertThat(AttributeCondition.lt(AttributeReference.resource("level"), "a").matches(request)).isFalse();
    }

    /**
     * 比较操作符支持右侧属性引用。
     */
    @Test
    void shouldCompareAgainstAnotherAttribute() {
        AuthorizationRequest request = new AuthorizationRequest(
                AccessSubject.ANONYMOUS,
                "order:read",
                AuthorizationResource.of("order", "1", Map.of("amount", 500)),
                Map.of("limit", 1000));

        AttributeCondition condition = new AttributeCondition(
                AttributeReference.resource("amount"),
                AttributeOperator.LT,
                AttributeReference.environment("limit"),
                Set.of());

        assertThat(condition.matches(request)).isTrue();
    }

    private AuthorizationRequest request(Map<String, Object> resourceAttributes) {
        return AuthorizationRequest.of(
                AccessSubject.ANONYMOUS,
                "order:read",
                AuthorizationResource.of("order", "1", resourceAttributes));
    }
}
