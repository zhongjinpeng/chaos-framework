package com.michael.chaos.autoconfigure.storage;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * 对象存储自动装配配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.storage")
public class ChaosStorageProperties {

    /**
     * 默认对象存储 provider；未设置时不自动注册 ObjectStorageClient。
     */
    private Provider provider = Provider.NONE;

    /**
     * 单个对象最大大小，例如 {@code 20MB}；为空表示不限制。大小未知的流式上传会在读取超限时中断。
     */
    private DataSize maxObjectSize;

    /**
     * 允许上传的 contentType 白名单，支持 {@code image/*} 通配；为空表示不限制。
     */
    private List<String> allowedContentTypes = new ArrayList<>();

    public DataSize getMaxObjectSize() {
        return maxObjectSize;
    }

    public void setMaxObjectSize(DataSize maxObjectSize) {
        this.maxObjectSize = maxObjectSize;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes == null ? new ArrayList<>() : allowedContentTypes;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider == null ? Provider.NONE : provider;
    }

    /**
     * 对象存储 provider。
     */
    public enum Provider {
        /**
         * 不自动创建对象存储客户端。
         */
        NONE,
        /**
         * 使用阿里云 OSS。
         */
        OSS,
        /**
         * 使用 MinIO。
         */
        MINIO
    }
}
