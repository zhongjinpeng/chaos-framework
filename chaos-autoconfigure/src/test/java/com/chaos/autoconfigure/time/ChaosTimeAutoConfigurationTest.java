package com.chaos.autoconfigure.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.core.time.DateTimeUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 时间自动装配测试。
 */
class ChaosTimeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosTimeAutoConfiguration.class));

    @Test
    void shouldUseAsiaShanghaiByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context).hasSingleBean(DateTimeUtils.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
            assertThat(context.getBean(DateTimeUtils.class).zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        });
    }

    @Test
    void shouldUseConfiguredZoneId() {
        contextRunner.withPropertyValues("chaos.time.zone-id=UTC")
                .run(context -> {
                    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("UTC"));
                    assertThat(context.getBean(DateTimeUtils.class).zoneId()).isEqualTo(ZoneId.of("UTC"));
                });
    }

    @Test
    void shouldBackOffForCustomClock() {
        contextRunner.withUserConfiguration(CustomClockConfiguration.class)
                .run(context -> {
                    Clock clock = context.getBean(Clock.class);
                    assertThat(clock.instant()).isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));
                    assertThat(context.getBean(DateTimeUtils.class).currentInstant()).isEqualTo(clock.instant());
                });
    }

    @Test
    void shouldBackOffForCustomDateTimeUtils() {
        contextRunner.withUserConfiguration(CustomDateTimeUtilsConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(DateTimeUtils.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomClockConfiguration {

        @Bean
        Clock customClock() {
            return Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDateTimeUtilsConfiguration {

        @Bean
        DateTimeUtils customDateTimeUtils() {
            return DateTimeUtils.system(ZoneId.of("UTC"));
        }
    }
}
