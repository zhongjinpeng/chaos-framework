package com.michael.chaos.autoconfigure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.core.idempotent.IdempotentRecordStore;
import com.michael.chaos.core.idempotent.IdempotentRepository;
import com.michael.chaos.core.idempotent.support.InMemoryIdempotentRecordStore;
import com.michael.chaos.core.ratelimit.RateLimiter;
import com.michael.chaos.web.config.ChaosWebProperties;
import com.michael.chaos.web.filter.CurrentSpanProvider;
import com.michael.chaos.web.filter.TraceFilter;
import com.michael.chaos.web.i18n.ErrorMessageResolver;
import com.michael.chaos.web.i18n.MessageSourceErrorMessageResolver;
import com.michael.chaos.web.idempotent.IdempotentResponseReplayFilter;
import com.michael.chaos.web.support.ClientIpResolver;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;

/**
 * Web 自动装配测试。
 */
class ChaosWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosWebAutoConfiguration.class));

    /**
     * 开发模式默认保留本地限流和幂等实现。
     */
    @Test
    void shouldRegisterLocalDefaultsInDevelopmentMode() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosWebProperties.class);
            assertThat(context).hasBean("accessLogFilter");
            assertThat(context).hasSingleBean(RateLimiter.class);
            assertThat(context).hasSingleBean(IdempotentRepository.class);
            assertThat(context).hasSingleBean(ClientIpResolver.class);
        });
    }

    /**
     * 耗时统计要能一个开关整体关掉，排查性能问题时不用逐个过滤器去关。
     */
    @Test
    void shouldDisableRequestTimingWithTheSingleSwitch() {
        contextRunner.withPropertyValues("chaos.web.request-timing-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("accessLogFilter"));
    }

    /**
     * 非 Servlet 应用（例如 WebFlux 网关）不应注册 Servlet 过滤器。
     */
    @Test
    void shouldNotApplyToNonServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ChaosWebProperties.class));
    }

    /**
     * XSS 输入转义默认关闭，需要显式开启。
     */
    @Test
    void shouldDisableXssFilterByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean("xssFilter"));
        contextRunner.withPropertyValues("chaos.web.xss-enabled=true")
                .run(context -> assertThat(context).hasBean("xssFilter"));
    }

    /**
     * 生产模式发现本地限流和幂等实现时默认阻断启动。
     */
    @Test
    void shouldRejectLocalDefaultsInProductionMode() {
        contextRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("InMemoryRateLimiter"));
    }

    /**
     * 显式关闭 fail-fast 时只告警。
     */
    @Test
    void shouldOnlyWarnWhenFailFastDisabled() {
        contextRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.fail-fast=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 迁移期可以显式放行危险默认实现。
     */
    @Test
    void shouldAllowLocalDefaultsWhenMigrationOverrideIsEnabled() {
        contextRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.allow-unsafe-defaults=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimiter.class);
                    assertThat(context).hasSingleBean(IdempotentRepository.class);
                });
    }

    /**
     * 幂等响应回放默认关闭，不注册快照存储与采集过滤器。
     *
     * <p>默认关闭是刻意的：开启后每个写请求的响应体都会多缓存一份，且每个幂等请求多一次快照查询，
     * 这个成本应该由使用方显式接受。</p>
     */
    @Test
    void shouldNotRegisterReplayComponentsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(IdempotentRecordStore.class);
            assertThat(context).doesNotHaveBean("idempotentResponseReplayFilter");
        });
    }

    /**
     * 开启回放后注册内存快照存储和采集过滤器，且过滤器排在安全过滤器链之后。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldRegisterReplayComponentsWhenEnabled() {
        contextRunner.withPropertyValues("chaos.web.idempotent.replay.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentRecordStore.class);
                    assertThat(context.getBean(IdempotentRecordStore.class))
                            .isInstanceOf(InMemoryIdempotentRecordStore.class);
                    FilterRegistrationBean<IdempotentResponseReplayFilter> registration =
                            context.getBean("idempotentResponseReplayFilter", FilterRegistrationBean.class);
                    assertThat(registration.getOrder()).isGreaterThan(SecurityProperties.DEFAULT_FILTER_ORDER);
                });
    }

    /**
     * 生产模式下内存快照存储同样被生产安全检查阻断：快照只在单个实例上，集群里回放会静默失效。
     */
    @Test
    void shouldRejectInMemoryRecordStoreInProductionMode() {
        contextRunner.withPropertyValues(
                        "chaos.web.idempotent.replay.enabled=true",
                        "chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("InMemoryIdempotentRecordStore"));
    }

    /**
     * 默认注册国际化解析器；显式关闭后退化为错误码默认文案。
     */
    @Test
    void shouldRegisterErrorMessageResolver() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ErrorMessageResolver.class);
            assertThat(context.getBean(ErrorMessageResolver.class))
                    .isInstanceOf(MessageSourceErrorMessageResolver.class);
        });
        contextRunner.withPropertyValues("chaos.web.i18n.enabled=false").run(context -> {
            assertThat(context.getBean(ErrorMessageResolver.class).resolve(CommonErrorCode.NOT_FOUND))
                    .isEqualTo("not found");
        });
    }

    /**
     * 固定语言配置生效，且下划线写法（{@code zh_CN}）与标准语言标签（{@code zh-CN}）都能识别。
     */
    @Test
    void shouldHonourFixedLocale() {
        contextRunner.withPropertyValues("chaos.web.i18n.default-locale=zh_CN").run(context -> {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            try {
                assertThat(context.getBean(ErrorMessageResolver.class).resolve(CommonErrorCode.NOT_FOUND))
                        .isEqualTo("请求的资源不存在");
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }
        });
    }

    /**
     * 存在 Micrometer Tracer 时注册 span 提供者，并把 TraceFilter 排到 Observation 过滤器之后。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldIntegrateWithMicrometerTracer() {
        contextRunner.withUserConfiguration(TracerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CurrentSpanProvider.class);
                    CurrentSpanProvider provider = context.getBean(CurrentSpanProvider.class);
                    assertThat(provider.currentSpan())
                            .hasValue(new CurrentSpanProvider.SpanIds("4bf92f3577b34da6a3ce929d0e0e4736", "a1b2c3d4e5f60718"));
                    FilterRegistrationBean<TraceFilter> registration =
                            context.getBean("traceFilter", FilterRegistrationBean.class);
                    assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
                });
    }

    /**
     * 没有外部追踪时 TraceFilter 位于最外层。
     */
    @Test
    @SuppressWarnings("unchecked")
    void traceFilterShouldBeOutermostWithoutTracer() {
        contextRunner.run(context -> {
            FilterRegistrationBean<TraceFilter> registration =
                    context.getBean("traceFilter", FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TracerConfiguration {

        @Bean
        Tracer tracer() {
            Tracer tracer = mock(Tracer.class);
            Span span = mock(Span.class);
            TraceContext traceContext = mock(TraceContext.class);
            when(traceContext.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
            when(traceContext.spanId()).thenReturn("a1b2c3d4e5f60718");
            when(span.context()).thenReturn(traceContext);
            when(tracer.currentSpan()).thenReturn(span);
            return tracer;
        }
    }
}
