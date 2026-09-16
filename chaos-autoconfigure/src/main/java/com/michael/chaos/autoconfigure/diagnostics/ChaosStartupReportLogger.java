package com.michael.chaos.autoconfigure.diagnostics;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * 容器刷新完成后输出一次 Chaos 启动报告。
 *
 * <p>监听 {@link ContextRefreshedEvent} 而不是 {@code ApplicationReadyEvent}：前者在测试的 ApplicationContextRunner
 * 和非 SpringApplication 启动场景中同样会发布。子容器（Feign、actuator 独立端口等）刷新时事件会冒泡到父容器，
 * 因此只处理自身容器的事件，并保证只输出一次。</p>
 */
public class ChaosStartupReportLogger implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.michael.chaos.StartupReport");

    private final ApplicationContext applicationContext;

    private final ChaosFeatureReporter reporter;

    private final ChaosDiagnosticsProperties.ReportLevel level;

    private final AtomicBoolean reported = new AtomicBoolean();

    /**
     * 创建启动报告输出器。
     */
    public ChaosStartupReportLogger(
            ApplicationContext applicationContext,
            ChaosFeatureReporter reporter,
            ChaosDiagnosticsProperties.ReportLevel level) {
        this.applicationContext = applicationContext;
        this.reporter = reporter;
        this.level = level;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != applicationContext || !reported.compareAndSet(false, true)) {
            return;
        }
        boolean enabled = level == ChaosDiagnosticsProperties.ReportLevel.DEBUG ? LOGGER.isDebugEnabled() : LOGGER.isInfoEnabled();
        if (!enabled) {
            return;
        }
        String text;
        try {
            text = ChaosStartupReportRenderer.render(reporter.build());
        } catch (RuntimeException ex) {
            // 报告只是辅助信息，构建失败不能影响应用启动。
            LOGGER.debug("Failed to build chaos startup report: {}", ex.getMessage(), ex);
            return;
        }
        if (level == ChaosDiagnosticsProperties.ReportLevel.DEBUG) {
            LOGGER.debug("\n{}", text);
        } else {
            LOGGER.info("\n{}", text);
        }
    }
}
