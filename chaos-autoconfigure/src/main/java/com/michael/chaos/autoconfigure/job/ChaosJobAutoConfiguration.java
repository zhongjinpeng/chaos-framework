package com.michael.chaos.autoconfigure.job;

import com.michael.chaos.autoconfigure.redis.ChaosRedisAutoConfiguration;
import com.michael.chaos.core.lock.DistributedLock;
import com.michael.chaos.job.DistributedJobRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * 定时任务自动装配。
 *
 * <p>仅开启 {@code @EnableScheduling} 无法解决多实例重复执行。本装配额外注册 {@link DistributedJobRunner}，
 * 业务在 {@code @Scheduled} 方法内通过它执行任务，获得分布式互斥、独立 traceId 和 MDC 任务名。
 * 需要在 Redis 自动装配之后执行，才能拿到 Redisson 分布式锁。</p>
 */
@AutoConfiguration(afterName = "com.michael.chaos.autoconfigure.redis.ChaosRedisAutoConfiguration")
@ConditionalOnClass(value = ScheduledAnnotationBeanPostProcessor.class, name = "com.michael.chaos.job.DistributedJobRunner")
@ConditionalOnProperty(prefix = "chaos.job", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ChaosJobProperties.class)

public class ChaosJobAutoConfiguration {

    /**
     * 注册分布式任务执行器；没有 {@link DistributedLock} 时本地执行并告警。
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedJobRunner distributedJobRunner(
            ObjectProvider<DistributedLock> distributedLock,
            ChaosJobProperties properties,
            @Value("${spring.application.name:application}") String appName) {
        return new DistributedJobRunner(
                distributedLock.getIfUnique(),
                properties.getLock().getKeyPrefix(),
                properties.getLock().getLeaseTime(),
                appName);
    }

    /**
     * 开启 Spring 调度。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "chaos.job", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfiguration {
    }
}
