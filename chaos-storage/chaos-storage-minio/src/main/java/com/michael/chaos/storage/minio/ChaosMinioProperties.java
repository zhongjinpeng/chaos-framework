package com.michael.chaos.storage.minio;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MinIO 对象存储配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.storage.minio")
public class ChaosMinioProperties {

    /**
     * 服务端地址。
     */
    @NotBlank(message = "chaos.storage.minio.endpoint must not be blank")
    private String endpoint;

    /**
     * 访问密钥。
     */
    @NotBlank(message = "chaos.storage.minio.access-key must not be blank")
    private String accessKey;

    /**
     * 访问密钥 Secret。
     */
    @NotBlank(message = "chaos.storage.minio.secret-key must not be blank")
    private String secretKey;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
