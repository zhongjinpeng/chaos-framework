package com.michael.chaos.security.auth;

import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.api.auth.LoginUserProvider;
import java.util.Optional;

/**
 * 基于 Spring Security {@code SecurityContextHolder} 的登录用户提供者。
 *
 * <p>把 {@link SecurityUtils#currentUser()} 适配为 {@link LoginUserProvider} SPI，
 * 供只依赖 chaos-security-api 的模块（如 chaos-mybatis 审计字段填充）读取当前用户。</p>
 */
public class SecurityContextLoginUserProvider implements LoginUserProvider {

    @Override
    public Optional<LoginUser> currentUser() {
        return SecurityUtils.currentUser();
    }
}
