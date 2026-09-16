package com.michael.chaos.storage.oss;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 阿里云 OSS 配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.storage.oss")
public class ChaosOssProperties {

    /**
     * OSS endpoint。
     */
    @NotBlank(message = "chaos.storage.oss.endpoint must not be blank")
    private String endpoint;

    /**
     * 访问密钥。
     */
    @NotBlank(message = "chaos.storage.oss.access-key-id must not be blank")
    private String accessKeyId;

    /**
     * 访问密钥 Secret。
     */
    @NotBlank(message = "chaos.storage.oss.access-key-secret must not be blank")
    private String accessKeySecret;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }
}
