package com.michael.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 *
 * <p>同时覆盖"什么都不配"这条路径：banner 被同时打包到 {@code classpath:banner.txt}
 * （Spring Boot 未配置 {@code spring.banner.location} 时的默认查找位置），
 * 业务方零配置即可拿到框架 banner。</p>
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
     * 不配置任何 banner 属性时也应打印框架 banner —— 走的是 classpath:banner.txt 这条默认查找路径。
     *
     * <p>这条断言的价值在于它会因为打包配置退化而失败：一旦 classpath 根上那份副本没了，
     * Spring Boot 会安静地回到自带 banner，没有任何报错。</p>
     */
    @Test
    void shouldRenderBannerWithoutAnyConfiguration(CapturedOutput output) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BannerTestApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.LOG)
                .properties("spring.application.name=banner-default-demo")
                .run()) {
            assertThat(context.isRunning()).isTrue();
        }

        assertThat(output).contains("Chaos Framework");
        assertThat(output).contains("banner-default-demo");
        assertThat(output.getOut()).doesNotContain("${").doesNotContain("@project.version@");
    }

    /**
     * 两个位置必须是同一份内容。
     *
     * <p>根上那份由 Maven 资源配置从 {@code com/michael/chaos/banner.txt} 复制而来，
     * 源文件只有一份；但"复制"这件事只存在于 POM 里，改错时表现是两处不一致
     * （例如只过滤了其中一份，版本号占位符留在另一份上），所以逐字节比一次。</p>
     */
    @Test
    void bothBannerLocationsShouldHoldTheSameContent() throws IOException {
        assertThat(read("banner.txt"))
                .as("classpath 根上的 banner 必须与 com/michael/chaos/banner.txt 完全一致")
                .isEqualTo(read("com/michael/chaos/banner.txt"));
    }

    private String read(String location) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(location)) {
            assertThat(in).as("%s 必须在 classpath 上", location).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 空应用：只为把 Spring 启动起来触发 banner 渲染。
     */
    @Configuration(proxyBeanMethods = false)
    static class BannerTestApplication {
    }
}
