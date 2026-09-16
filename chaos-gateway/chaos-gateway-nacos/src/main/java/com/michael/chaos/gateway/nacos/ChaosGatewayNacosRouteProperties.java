package com.michael.chaos.gateway.nacos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway Nacos 动态路由配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.gateway.nacos-routes")
public class ChaosGatewayNacosRouteProperties {

    /**
     * 是否启用 Nacos 动态路由。
     */
    private boolean enabled = false;

    /**
     * 路由配置 dataId。
     */
    @NotBlank(message = "chaos.gateway.nacos-routes.data-id must not be blank")
    private String dataId = "chaos-gateway-routes.yaml";

    /**
     * 路由配置 group。
     */
    @NotBlank(message = "chaos.gateway.nacos-routes.group must not be blank")
    private String group = "DEFAULT_GROUP";

    /**
     * 初始拉取配置超时时间，单位毫秒。
     */
    @Min(value = 1, message = "chaos.gateway.nacos-routes.timeout-ms must be greater than 0")
    private long timeoutMs = 3_000;

    /**
     * 初始加载失败时是否阻止应用启动。
     */
    private boolean failFast = false;

    /**
     * Nacos 配置为空时是否清空当前动态路由。
     */
    private boolean clearOnEmpty = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isClearOnEmpty() {
        return clearOnEmpty;
    }

    public void setClearOnEmpty(boolean clearOnEmpty) {
        this.clearOnEmpty = clearOnEmpty;
    }
}
