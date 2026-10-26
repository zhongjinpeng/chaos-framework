package com.chaos.autoconfigure.process;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 流程引擎自动装配配置。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.process")
public class ChaosProcessProperties {

    /**
     * 是否启用流程引擎自动装配。
     */
    private boolean enabled = true;

    /**
     * 自动创建的流程引擎类型。内存实现仅用于开发和测试；生产环境必须提供持久化实现。
     */
    private EngineType engine = EngineType.MEMORY;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public EngineType getEngine() {
        return engine;
    }

    public void setEngine(EngineType engine) {
        this.engine = engine == null ? EngineType.NONE : engine;
    }

    /**
     * 可由框架自动创建的流程引擎类型。
     */
    public enum EngineType {
        /** 不自动创建流程引擎，由应用提供实现。 */
        NONE,
        /** 使用单进程内存实现，仅适用于开发和测试。 */
        MEMORY,
        /** 使用 JDBC 持久化实现。 */
        JDBC,
        /** 使用 Redis 持久化实现。 */
        REDIS
    }
}
