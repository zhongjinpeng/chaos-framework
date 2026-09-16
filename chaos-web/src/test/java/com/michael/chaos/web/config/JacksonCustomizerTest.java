package com.michael.chaos.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson 定制器测试。
 */
class JacksonCustomizerTest {

    /**
     * 定制器只能追加模块，不能覆盖此前注册的模块（例如 Boot 注册的 Module Bean）。
     */
    @Test
    void shouldKeepPreviouslyRegisteredModules() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        SimpleModule customModule = new SimpleModule("custom-module");
        customModule.addSerializer(Marker.class, new com.fasterxml.jackson.databind.ser.std.ToStringSerializer());
        builder.modulesToInstall(customModule);

        new JacksonCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        assertThat(mapper.getRegisteredModuleIds()).contains("custom-module");
        String json = mapper.writeValueAsString(Map.of(
                "date", LocalDate.of(2026, 1, 2),
                "marker", new Marker()));
        assertThat(json).contains("\"2026-01-02\"").contains("\"marker!\"");
    }

    static class Marker {
        @Override
        public String toString() {
            return "marker!";
        }
    }
}
