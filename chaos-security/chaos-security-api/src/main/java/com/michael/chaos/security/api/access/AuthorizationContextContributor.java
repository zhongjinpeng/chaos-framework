package com.michael.chaos.security.api.access;

import java.util.Map;

/**
 * 授权环境属性贡献者。
 *
 * <p>在构造 {@link AuthorizationRequest} 之前被依次调用，用于把请求 IP、当前时间、链路信息等
 * 写入 ENVIRONMENT 命名空间，供 ABAC 条件引用。</p>
 */
@FunctionalInterface
public interface AuthorizationContextContributor {

    /**
     * 向环境属性中写入本贡献者负责的属性。
     *
     * @param environment 可变的环境属性容器
     */
    void contribute(Map<String, Object> environment);
}
