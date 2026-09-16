package com.michael.chaos.autoconfigure.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis starter 配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.redis")
public class ChaosRedisProperties {

    /**
     * 幂等、分布式锁、限流、缓存 key、布隆过滤器和延迟队列的统一 key 前缀。
     *
     * <p>多个服务共用同一个 Redis 时强烈建议设置为应用名（例如 {@code ${spring.application.name}}），
     * 否则同名 key 会互相冲突。默认空字符串，与旧版本 key 保持兼容；修改前缀后旧 key 不再生效，
     * 请在发布窗口内评估锁互斥和延迟队列存量数据。JWT 黑名单需要跨服务共享，不受该前缀影响。</p>
     */
    private String keyPrefix = "";

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }
}
