package com.michael.chaos.core.diagnostic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.michael.chaos.core.constant.ProductionProfiles;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 诊断信息格式测试。
 */
class ChaosDiagnosticTest {

    /**
     * 格式化文本必须包含问题、原因、修复三段，便于使用方直接照着修。
     */
    @Test
    void shouldFormatProblemCausesAndFixes() {
        ChaosDiagnostic diagnostic = new ChaosDiagnostic(
                "缺少配置",
                List.of("原因一", "原因二"),
                List.of("配置 chaos.x"));

        assertEquals("""
                问题：缺少配置
                原因：
                  - 原因一
                  - 原因二
                怎么修：
                  - 配置 chaos.x""", diagnostic.format());
    }

    /**
     * 没有原因或修复建议时省略对应段落。
     */
    @Test
    void shouldOmitEmptySections() {
        assertEquals("问题：只有问题", ChaosDiagnostic.of("只有问题", null, null).format());
    }

    /**
     * 异常消息即格式化文本，并且仍然是 IllegalStateException。
     */
    @Test
    void exceptionShouldCarryFormattedMessage() {
        ChaosDiagnostic diagnostic = ChaosDiagnostic.of("问题", "原因", "修复");
        ChaosDiagnosticException exception = new ChaosDiagnosticException(diagnostic);

        assertInstanceOf(IllegalStateException.class, exception);
        assertEquals(diagnostic.format(), exception.getMessage());
        assertSame(diagnostic, exception.getDiagnostic());
    }

    /**
     * 默认生产 profile 包含 prd，且数组副本互不影响。
     */
    @Test
    void defaultProductionProfilesShouldIncludePrd() {
        String[] profiles = ProductionProfiles.defaultsArray();
        profiles[0] = "changed";

        assertEquals(List.of("prod", "production", "prd"), ProductionProfiles.DEFAULTS);
        assertArrayEquals(new String[] {"prod", "production", "prd"}, ProductionProfiles.defaultsArray());
    }
}
