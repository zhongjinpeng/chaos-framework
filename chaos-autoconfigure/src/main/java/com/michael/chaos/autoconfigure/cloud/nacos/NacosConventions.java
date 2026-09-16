package com.michael.chaos.autoconfigure.cloud.nacos;

/**
 * Nacos 命名约定。
 */
public class NacosConventions {

    private final ChaosNacosProperties properties;

    /**
     * 创建 Nacos 命名约定。
     */
    public NacosConventions(ChaosNacosProperties properties) {
        this.properties = properties;
    }

    /**
     * 构建应用配置 dataId。
     *
     * @param applicationName 应用名称
     * @param profile 环境标识
     * @return Nacos dataId
     */
    public String dataId(String applicationName, String profile) {
        String resolvedProfile = profile == null || profile.isBlank() ? "default" : profile.trim();
        return properties.getDataIdPrefix() + "-" + applicationName + "-" + resolvedProfile + ".yaml";
    }

    /**
     * 返回灰度元数据键。
     */
    public String grayMetadataKey() {
        return properties.getMetadataPrefix() + ".gray";
    }

    /**
     * 返回租户元数据键。
     */
    public String tenantMetadataKey() {
        return properties.getMetadataPrefix() + ".tenant";
    }
}
