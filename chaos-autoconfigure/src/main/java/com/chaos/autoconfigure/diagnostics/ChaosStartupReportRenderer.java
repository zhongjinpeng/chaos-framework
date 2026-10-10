package com.chaos.autoconfigure.diagnostics;

import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.EndpointStatus;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.EndpointUrl;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.RuntimeDetails;
import java.util.stream.Collectors;

/**
 * 把 {@link ChaosFeatureReport} 渲染为紧凑的启动日志文本。
 *
 * <p>设计目标是"一屏看懂"：只展示应用身份、运行地址以及需要处理的诊断提示。完整功能状态、
 * 配置明细和无问题时的空诊断统一通过 Actuator Health 查询，避免启动日志被静态能力清单淹没。</p>
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
        appendHeader(builder, report);
        appendRuntime(builder, report.runtime());
        appendFindings(builder, report);
        return builder.toString();
    }

    /**
     * 运行环境与完整访问地址。
     */
    private static void appendRuntime(StringBuilder builder, RuntimeDetails runtime) {
        if (runtime == null || "UNKNOWN".equals(runtime.webApplicationType())) {
            return;
        }
        builder.append("\n  运行环境  类型=").append(runtime.webApplicationType())
                .append(" | 绑定地址=").append(runtime.bindAddress());
        if (runtime.applicationPort() != null) {
            builder.append(" | 应用端口=").append(runtime.applicationPort());
        }
        if (runtime.managementPort() != null) {
            builder.append(" | 管理端口=").append(runtime.managementPort());
        }
        if (!runtime.endpoints().isEmpty()) {
            builder.append("\n  访问地址（").append(runtime.endpoints().size()).append("）");
            for (EndpointUrl endpoint : runtime.endpoints()) {
                builder.append("\n    ").append(pad(endpoint.name()))
                        .append(endpoint.url())
                        .append(" [").append(statusLabel(endpoint.status())).append(']');
            }
        }
    }

    /**
     * 报告头：应用、profile、生产模式与 fail-fast 开关。
     */
    private static void appendHeader(StringBuilder builder, ChaosFeatureReport report) {
        builder.append("Chaos 启动报告 | 应用 ").append(report.application())
                .append(" | profile ").append(report.activeProfiles().isEmpty() ? "[default]" : report.activeProfiles())
                .append(" | 生产模式 ").append(report.productionMode() ? "是" : "否")
                .append(" | fail-fast ").append(report.failFast() ? "开" : "关");
        if (!report.identifiers().isEmpty()) {
            // 自定义标识另起一行：数量由使用方决定，塞进首行会把本来就长的报告头挤成一大坨。
            builder.append("\n  标识  ").append(report.identifiers().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(" | ")));
        }
    }

    /**
     * 诊断提示：没有问题时不占日志空间；存在问题时每条都带"怎么修"。
     */
    private static void appendFindings(StringBuilder builder, ChaosFeatureReport report) {
        if (report.findings().isEmpty()) {
            return;
        }
        builder.append("\n  诊断（").append(report.findings().size()).append("）");
        for (Finding finding : report.findings()) {
            builder.append("\n    [").append(finding.severity()).append("] ").append(finding.feature())
                    .append("：").append(finding.problem())
                    .append("\n           怎么修：").append(finding.fix());
        }
    }

    private static String statusLabel(EndpointStatus status) {
        return switch (status) {
            case ENABLED -> "已启用";
            case EXPOSED -> "已暴露";
            case NOT_EXPOSED -> "未暴露";
            case DISABLED -> "已关闭";
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
