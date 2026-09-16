package com.michael.chaos.autoconfigure.cloud;

import com.michael.chaos.trace.feign.TraceFeignRequestInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * Cloud starter 自动装配。
 */
@AutoConfiguration
@ConditionalOnClass(value = RequestInterceptor.class, name = "com.michael.chaos.trace.feign.TraceFeignRequestInterceptor")
@EnableFeignClients

public class ChaosCloudAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceFeignRequestInterceptor traceFeignRequestInterceptor() {
        return new TraceFeignRequestInterceptor();
    }
}
