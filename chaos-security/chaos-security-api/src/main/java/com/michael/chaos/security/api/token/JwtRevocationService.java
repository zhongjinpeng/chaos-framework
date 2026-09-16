package com.michael.chaos.security.api.token;

import java.time.Duration;

/**
 * JWT 撤销（黑名单）服务 SPI。
 *
 * <p>授权服务器写入、资源服务器与网关读取同一份黑名单，因此接口只以 token 标识（jti 或 token 摘要）为参数，
 * 不依赖 Spring Security 的 {@code Jwt} 类型：网关（WebFlux）、资源服务（Servlet）和持久层都可以直接依赖本接口，
 * 而不必把 chaos-security 的 Servlet 过滤器、AOP 等实现带进 classpath。token 标识统一由 {@link JwtTokenIds} 生成。</p>
 */
public interface JwtRevocationService {

    /**
     * 判断 token 标识是否已被撤销。
     *
     * @param tokenId {@link JwtTokenIds#resolve(String, String)} 生成的 token 标识
     * @return 已撤销返回 {@code true}
     */
    boolean isRevoked(String tokenId);

    /**
     * 撤销指定 token 标识。
     *
     * @param tokenId token 标识
     * @param ttl 黑名单保留时间，通常为 token 剩余有效期；为空或非正数时实现应使用一个极短的兜底值
     */
    void revoke(String tokenId, Duration ttl);
}
