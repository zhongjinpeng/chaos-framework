package com.michael.chaos.security.api.auth;

import java.util.Optional;

/**
 * 当前登录用户提供者 SPI。
 *
 * <p>持久层（审计字段填充）、消息、任务等非 Web 模块需要知道“当前是谁”，但不应依赖 Spring Security。
 * 这些模块只依赖本接口，由 chaos-security 提供基于 {@code SecurityContextHolder} 的实现，
 * 从而切断 chaos-mybatis 等模块到 chaos-security 的直接依赖。</p>
 */
@FunctionalInterface
public interface LoginUserProvider {

    /**
     * 获取当前已认证用户。
     *
     * @return 未认证时返回空
     */
    Optional<LoginUser> currentUser();
}
