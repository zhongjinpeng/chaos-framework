package com.chaos.autoconfigure.time;

import com.chaos.core.time.DateTimeUtils;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 应用业务时钟与日期时间工具自动装配。
 */
@AutoConfiguration
@EnableConfigurationProperties(ChaosTimeProperties.class)
public class ChaosTimeAutoConfiguration {

    /**
     * 创建使用业务时区的系统时钟。
     *
     * @param properties 应用时间配置
     * @return 使用配置时区的系统时钟
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock chaosClock(ChaosTimeProperties properties) {
        return Clock.system(properties.getZoneId());
    }

    /**
     * 创建统一读取当前业务时间的工具。
     *
     * @param clock 应用时钟，可由宿主应用或测试覆盖
     * @return 基于应用时钟的日期时间工具
     */
    @Bean
    @ConditionalOnMissingBean
    public DateTimeUtils chaosDateTimeUtils(Clock clock) {
        return new DateTimeUtils(clock);
    }
}
