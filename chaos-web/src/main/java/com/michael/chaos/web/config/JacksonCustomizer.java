package com.michael.chaos.web.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson 序列化配置定制器。
 *
 * <p>注意必须使用 {@link Jackson2ObjectMapperBuilder#modulesToInstall} 追加模块。
 * 原实现使用 {@code builder.modules(...)}，该方法会<b>替换</b>全部已注册模块并关闭模块自动发现，
 * 导致 Boot 注册的 Jdk8Module、ParameterNamesModule、{@code @JsonComponent}、{@code @JsonMixin}
 * 以及用户自定义 Module Bean 全部静默失效。</p>
 */
public class JacksonCustomizer implements Jackson2ObjectMapperBuilderCustomizer, Ordered {

    /**
     * 追加 Java 时间类型模块，并禁用时间戳格式输出。
     */
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        builder.modulesToInstall(JavaTimeModule.class);
        builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 在 Boot 标准定制器（order=0）之后执行，只追加配置而不覆盖用户显式设置。
     */
    @Override
    public int getOrder() {
        return 10;
    }
}
