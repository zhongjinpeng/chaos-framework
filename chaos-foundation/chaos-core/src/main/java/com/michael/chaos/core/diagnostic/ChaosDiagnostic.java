package com.michael.chaos.core.diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * 面向使用方的可操作诊断信息：问题 / 原因 / 怎么修。
 *
 * <p>框架启动失败或运行期配置错误时，只抛出 {@code "xxx requires yyy"} 这类英文短句，使用方仍需要翻源码才知道
 * 该补哪个 starter、改哪个配置。统一用本类描述问题，保证每条提示都能直接照着修。</p>
 *
 * <p>本类不依赖 Spring，功能库（mybatis、mq、authorization 等）也可以使用。</p>
 *
 * @param problem 发生了什么（一句话）
 * @param causes 为什么会发生，可以有多条
 * @param fixes 怎么修，可以有多条，建议给出具体的 starter 或配置项
 */
public record ChaosDiagnostic(String problem, List<String> causes, List<String> fixes) {

    /**
     * 规范化参数，列表不可变。
     */
    public ChaosDiagnostic {
        Objects.requireNonNull(problem, "problem must not be null");
        causes = causes == null ? List.of() : List.copyOf(causes);
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
    }

    /**
     * 创建单条原因、单条修复建议的诊断。
     */
    public static ChaosDiagnostic of(String problem, String cause, String fix) {
        return new ChaosDiagnostic(problem, cause == null ? List.of() : List.of(cause), fix == null ? List.of() : List.of(fix));
    }

    /**
     * 格式化为多行文本：
     *
     * <pre>
     * 问题：...
     * 原因：
     *   - ...
     * 怎么修：
     *   - ...
     * </pre>
     */
    public String format() {
        StringBuilder builder = new StringBuilder("问题：").append(problem);
        appendSection(builder, "原因", causes);
        appendSection(builder, "怎么修", fixes);
        return builder.toString();
    }

    private static void appendSection(StringBuilder builder, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        builder.append('\n').append(title).append('：');
        for (String item : items) {
            builder.append("\n  - ").append(item);
        }
    }

    @Override
    public String toString() {
        return format();
    }
}
