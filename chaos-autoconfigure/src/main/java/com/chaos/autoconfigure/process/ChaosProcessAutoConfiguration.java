package com.chaos.autoconfigure.process;

import com.chaos.autoconfigure.support.ProductionSafetyEnforcer;
import com.chaos.autoconfigure.support.UnsafeForProduction;
import com.chaos.core.metrics.ChaosMetrics;
import com.chaos.process.ProcessEngine;
import com.chaos.process.statemachine.StateMachineEngine;
import com.chaos.process.support.InMemoryProcessEngine;
import com.chaos.process.jdbc.JdbcProcessEngine;
import com.chaos.process.redis.RedisProcessEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 流程引擎自动装配。
 *
 * <p>开发和测试环境默认提供内存实现；业务提供任意 {@link ProcessEngine} Bean 时自动回退。
 * 内存实现会被统一生产安全检查识别，生产环境默认阻断启动。</p>
 */
@AutoConfiguration(afterName = "com.chaos.autoconfigure.metrics.ChaosMetricsAutoConfiguration")
@ConditionalOnClass(name = "com.chaos.process.ProcessEngine")
@ConditionalOnProperty(prefix = "chaos.process", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ChaosProcessProperties.class)
public class ChaosProcessAutoConfiguration {

    /**
     * 注册接入统一治理指标的无状态状态机引擎。
     */
    @Bean
    @ConditionalOnMissingBean(StateMachineEngine.class)
    public StateMachineEngine stateMachineEngine(ObjectProvider<ChaosMetrics> metricsProvider) {
        return new StateMachineEngine(metricsProvider.getIfAvailable());
    }

    /**
     * 注册流程能力的生产安全检查器。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosProcessProductionSafetyChecker")
    public SmartInitializingSingleton chaosProcessProductionSafetyChecker(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory) {
        return new ProductionSafetyEnforcer(environment, beanFactory);
    }

    /**
     * 仅在流程库存在且选择内存引擎时加载开发运行时。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "com.chaos.process.ProcessEngine",
            "com.chaos.process.support.InMemoryProcessEngine"
    })
    @ConditionalOnProperty(
            prefix = "chaos.process",
            name = "engine",
            havingValue = "memory",
            matchIfMissing = true)
    static class InMemoryConfiguration {

        /**
         * 注册单进程内存流程引擎。
         */
        @Bean
        @ConditionalOnMissingBean(ProcessEngine.class)
        @UnsafeForProduction(
                value = InMemoryProcessEngine.class,
                message = "chaos-process: 生产环境不能使用 InMemoryProcessEngine，"
                        + "请提供具备事务、乐观锁和幂等约束的持久化 ProcessEngine 实现")
        InMemoryProcessEngine inMemoryProcessEngine(ObjectProvider<ChaosMetrics> metricsProvider) {
            return new InMemoryProcessEngine(Clock.systemUTC(), metricsProvider.getIfAvailable());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({JdbcOperations.class, JdbcProcessEngine.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "chaos.process", name = "engine", havingValue = "jdbc")
    static class JdbcConfiguration {
        @Bean
        @ConditionalOnBean(JdbcOperations.class)
        @ConditionalOnMissingBean(ProcessEngine.class)
        JdbcProcessEngine jdbcProcessEngine(JdbcOperations jdbc, ObjectProvider<ObjectMapper> mapper,
                ObjectProvider<PlatformTransactionManager> transactionManagers) {
            PlatformTransactionManager manager = transactionManagers.getIfUnique();
            return new JdbcProcessEngine(jdbc, mapper.getIfAvailable(ObjectMapper::new), "chaos_process_",
                    manager == null ? null : new TransactionTemplate(manager));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({StringRedisTemplate.class, RedisProcessEngine.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "chaos.process", name = "engine", havingValue = "redis")
    static class RedisConfiguration {
        @Bean
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnMissingBean(ProcessEngine.class)
        RedisProcessEngine redisProcessEngine(StringRedisTemplate redis, ObjectProvider<ObjectMapper> mapper) {
            return new RedisProcessEngine(redis, mapper.getIfAvailable(ObjectMapper::new));
        }
    }
}
