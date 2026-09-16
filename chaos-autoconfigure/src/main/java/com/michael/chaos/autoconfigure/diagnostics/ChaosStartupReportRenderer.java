package com.michael.chaos.autoconfigure.diagnostics;

import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.DisabledCategory;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Feature;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把 {@link ChaosFeatureReport} 渲染为紧凑的启动日志文本。
 *
 * <p>设计目标是"一屏看懂"：已启用功能每个一行并带关键配置；未启用功能按原因归类合并成一行，
 * 缺少依赖的功能通常是有意不引入，不逐条展开；诊断提示放在最后并按严重程度排序。</p>
 */
public final class ChaosStartupReportRenderer {

    private static final int NAME_WIDTH = 16;

    private ChaosStartupReportRenderer() {
    }

    /**
     * 渲染报告。
     */
    public static String render(ChaosFeatureReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("Chaos 启动报告 | 应用 ").append(report.application())
                .append(" | profile ").append(report.activeProfiles().isEmpty() ? "[default]" : report.activeProfiles())
                .append(" | 生产模式 ").append(report.productionMode() ? "是" : "否")
                .append(" | fail-fast ").append(report.failFast() ? "开" : "关");

        List<Feature> enabled = report.enabledFeatures();
        builder.append("\n  已启用（").append(enabled.size()).append("）");
        if (enabled.isEmpty()) {
            builder.append("\n    无：没有引入任何 chaos 功能 starter");
        }
        for (Feature feature : enabled) {
            builder.append("\n    ").append(pad(feature.name()));
            if (!feature.settings().isEmpty()) {
                builder.append(feature.settings().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(", ")));
            }
        }

        Map<DisabledCategory, List<Feature>> disabled = new EnumMap<>(DisabledCategory.class);
        report.features().stream()
                .filter(feature -> !feature.enabled())
                .forEach(feature -> disabled.computeIfAbsent(feature.category(), key -> new ArrayList<>()).add(feature));
        if (!disabled.isEmpty()) {
            builder.append("\n  未启用");
            disabled.forEach((category, features) -> {
                builder.append("\n    ").append(pad(label(category)));
                if (category == DisabledCategory.MISSING_DEPENDENCY || category == DisabledCategory.WEB_APPLICATION_TYPE) {
                    builder.append(features.stream().map(Feature::name).collect(Collectors.joining(", ")));
                } else {
                    builder.append(features.stream()
                            .map(feature -> feature.name() + "（" + feature.reason() + "）")
                            .collect(Collectors.joining("; ")));
                }
            });
        }

        builder.append("\n  诊断（").append(report.findings().size()).append("）");
        if (report.findings().isEmpty()) {
            builder.append("\n    未发现问题");
        }
        for (Finding finding : report.findings()) {
            builder.append("\n    [").append(finding.severity()).append("] ").append(finding.feature())
                    .append("：").append(finding.problem())
                    .append("\n           怎么修：").append(finding.fix());
        }
        builder.append("\n  查看完整报告：暴露 actuator 端点 chaos（management.endpoints.web.exposure.include）后访问 /actuator/chaos");
        return builder.toString();
    }

    private static String label(DisabledCategory category) {
        return switch (category) {
            case MISSING_DEPENDENCY -> "缺少依赖";
            case WEB_APPLICATION_TYPE -> "应用类型不符";
            case DISABLED_BY_PROPERTY -> "被配置关闭";
            case EXCLUDED -> "被排除";
            case NOT_IMPORTED -> "未导入";
            case OTHER, NONE -> "其他";
        };
    }

    /**
     * 按终端显示宽度补齐（中文字符占两列），保证中英文标签后的内容对齐。
     */
    static String pad(String name) {
        StringBuilder builder = new StringBuilder(name);
        int width = name.codePoints().map(codePoint -> codePoint > 0x2E7F ? 2 : 1).sum();
        for (int i = width; i < NAME_WIDTH; i++) {
            builder.append(' ');
        }
        return builder.append(' ').toString();
    }
}
