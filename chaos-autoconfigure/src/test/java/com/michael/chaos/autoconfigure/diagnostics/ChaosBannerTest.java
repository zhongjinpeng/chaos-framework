package com.michael.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 框架内置 banner 的渲染测试。
 *
 * <p>真的启动一个 Spring 应用来渲染，而不是只读文件：banner 里既有构建期由 Maven 资源过滤替换的
 * {@code @project.version@}，又有运行期由 Spring 解析的 {@code ${...}}，两套占位符互相踩到的话
 * 只有真渲染一次才看得出来。</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class ChaosBannerTest {

    /**
     * 按 archetype 生成项目的配置启用 banner，应打印出框架版本且不残留任何未解析的占位符。
     */
    @Test
    void shouldRenderBannerWithFrameworkVersion(CapturedOutput output) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BannerTestApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.LOG)
                .properties(
                        "spring.banner.location=classpath:com/michael/chaos/banner.txt",
                        "spring.application.name=banner-demo")
                .run()) {
            assertThat(context.isRunning()).isTrue();
        }

        assertThat(output).contains("Chaos Framework");
        assertThat(output).contains("banner-demo");
        assertThat(output).containsPattern("v\\d+\\.\\d+\\.\\d+");
        // 未解析的占位符会被 Spring 原样打印出来，这是 banner 写错时最常见的表现。
        assertThat(output.getOut()).doesNotContain("${").doesNotContain("@project.version@");
    }

    /**
     * 空应用：只为把 Spring 启动起来触发 banner 渲染。
     */
    @Configuration(proxyBeanMethods = false)
    static class BannerTestApplication {
    }
}
