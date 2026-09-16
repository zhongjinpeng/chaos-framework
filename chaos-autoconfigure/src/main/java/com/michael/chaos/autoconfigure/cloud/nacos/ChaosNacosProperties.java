package com.michael.chaos.autoconfigure.cloud.nacos;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Nacos 治理配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.nacos")
public class ChaosNacosProperties {

    /**
     * 是否启用 chaos 对 Nacos 的约定配置。
     */
    private boolean enabled = true;

    /**
     * 配置中心 dataId 前缀。
     */
    @NotBlank(message = "chaos.nacos.data-id-prefix must not be blank")
    private String dataIdPrefix = "chaos";

    /**
     * 配置中心 group。
     */
    @NotBlank(message = "chaos.nacos.group must not be blank")
    private String group = "DEFAULT_GROUP";

    /**
     * 服务实例元数据键名前缀。
     */
    @NotBlank(message = "chaos.nacos.metadata-prefix must not be blank")
    private String metadataPrefix = "chaos";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataIdPrefix() {
        return dataIdPrefix;
    }

    public void setDataIdPrefix(String dataIdPrefix) {
        this.dataIdPrefix = dataIdPrefix;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public void setMetadataPrefix(String metadataPrefix) {
        this.metadataPrefix = metadataPrefix;
    }
}
