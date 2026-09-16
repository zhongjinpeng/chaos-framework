package com.michael.chaos.mybatis.audit;

import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.api.auth.LoginUserProvider;
import java.util.Objects;

/**
 * 从登录用户读取操作人的实现，取不到登录用户时回退到另一个提供者。
 *
 * <p>只依赖 chaos-security-api 的 {@link LoginUserProvider} SPI，不再直接调用 chaos-security 的
 * {@code SecurityUtils}：持久层不应感知 Spring Security，具体用户来源由安全模块注册的实现决定。</p>
 */
public class LoginUserAuditorProvider implements AuditorProvider {

    private final LoginUserProvider loginUserProvider;

    private final AuditorProvider fallback;

    /**
     * 创建操作人提供者。
     *
     * @param loginUserProvider 登录用户提供者
     * @param fallback 未登录（定时任务、消息消费等）时的回退提供者
     */
    public LoginUserAuditorProvider(LoginUserProvider loginUserProvider, AuditorProvider fallback) {
        this.loginUserProvider = Objects.requireNonNull(loginUserProvider, "loginUserProvider must not be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    @Override
    public String currentAuditor() {
        return loginUserProvider.currentUser()
                .map(LoginUser::userId)
                .filter(userId -> !userId.isBlank())
                .orElseGet(fallback::currentAuditor);
    }
}
