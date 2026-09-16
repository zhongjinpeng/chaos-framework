package com.michael.chaos.autoconfigure.diagnostics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Chaos 启动诊断配置。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.diagnostics")
public class ChaosDiagnosticsProperties {

    /**
     * 启动报告配置。
     */
    @Valid
    @NotNull(message = "chaos.diagnostics.startup-report must not be null")
    private StartupReport startupReport = new StartupReport();

    /**
     * Web 运行栈检查配置。
     */
    @Valid
    @NotNull(message = "chaos.diagnostics.web-stack-check must not be null")
    private WebStackCheck webStackCheck = new WebStackCheck();

    /**
     * outbox 表结构检查配置。
     */
    @Valid
    @NotNull(message = "chaos.diagnostics.outbox-schema-check must not be null")
    private OutboxSchemaCheck outboxSchemaCheck = new OutboxSchemaCheck();

    public StartupReport getStartupReport() {
        return startupReport;
    }

    public void setStartupReport(StartupReport startupReport) {
        this.startupReport = startupReport == null ? new StartupReport() : startupReport;
    }

    public WebStackCheck getWebStackCheck() {
        return webStackCheck;
    }

    public void setWebStackCheck(WebStackCheck webStackCheck) {
        this.webStackCheck = webStackCheck == null ? new WebStackCheck() : webStackCheck;
    }

    public OutboxSchemaCheck getOutboxSchemaCheck() {
        return outboxSchemaCheck;
    }

    public void setOutboxSchemaCheck(OutboxSchemaCheck outboxSchemaCheck) {
        this.outboxSchemaCheck = outboxSchemaCheck == null ? new OutboxSchemaCheck() : outboxSchemaCheck;
    }

    /**
     * 启动报告配置。
     */
    public static class StartupReport {

        /**
         * 是否在应用启动完成后输出 Chaos 启动报告（已启用功能、关键配置、诊断提示），默认开启。
         */
        private boolean enabled = true;

        /**
         * 追加到启动报告抬头的自定义标识（如版本号、构建号、机房），按声明顺序渲染，默认为空。
         *
         * <p>中文等非「小写字母/数字/短横线」的 key 必须写成 {@code "[中文]"}，否则宽松绑定会剥掉这些字符
         * 导致绑定失败。运行期才知道的值（hostname、Pod 名）改用 {@code ChaosStartupIdentifierContributor}
         * Bean，同名 key 以配置为准。值按与其他配置相同的规则脱敏。详见 docs/diagnostics.md。</p>
         */
        private Map<String, String> identifiers = new LinkedHashMap<>();

        /**
         * 启动报告的日志级别：INFO 或 DEBUG；设为 DEBUG 时只在开启 debug 日志后可见。
         */
        @NotNull(message = "chaos.diagnostics.startup-report.level must not be null")
        private ReportLevel level = ReportLevel.INFO;

        public Map<String, String> getIdentifiers() {
            return identifiers;
        }

        public void setIdentifiers(Map<String, String> identifiers) {
            this.identifiers = identifiers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(identifiers);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public ReportLevel getLevel() {
            return level;
        }

        public void setLevel(ReportLevel level) {
            this.level = level == null ? ReportLevel.INFO : level;
        }
    }

    /**
     * Web 运行栈检查配置。
     */
    public static class WebStackCheck {

        /**
         * Servlet 应用的类路径中存在 chaos-gateway（只能运行在 WebFlux 上）时是否阻断启动，默认开启。
         *
         * <p>同时引入 chaos-web-service-starter 与 chaos-gateway-starter 时 Spring Boot 会选择 Servlet，
         * 网关治理会被静默跳过；开启后启动即失败并说明应该移除哪个 starter。</p>
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * outbox 表结构检查配置。
     */
    public static class OutboxSchemaCheck {

        /**
         * 存在 JDBC outbox 仓储时是否在启动阶段检查 outbox 表是否存在；缺表时输出带建表脚本路径的 WARN，默认开启。
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 启动报告日志级别。
     */
    public enum ReportLevel {
        /**
         * INFO 级别，默认可见。
         */
        INFO,
        /**
         * DEBUG 级别，只在开启 debug 日志后可见。
         */
        DEBUG
    }
}
