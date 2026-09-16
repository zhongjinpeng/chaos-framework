package com.michael.chaos.authorization.password;

/**
 * 登录失败次数限制端口。
 *
 * <p>用于在 token 端点防御暴力破解和撞库：连续失败达到阈值后在锁定期内直接拒绝，
 * 不再调用业务身份服务校验密码。实现必须是线程安全的，集群部署应使用共享存储（如 Redis）。</p>
 */
public interface LoginFailureLimiter {

    /**
     * 判断登录主体当前是否处于锁定状态。
     *
     * @param key 登录主体标识（通常为租户 + 用户名）
     * @return 已锁定时返回 {@code true}
     */
    boolean isLocked(String key);

    /**
     * 记录一次认证失败。
     *
     * @param key 登录主体标识
     */
    void recordFailure(String key);

    /**
     * 认证成功后清除失败计数。
     *
     * @param key 登录主体标识
     */
    void reset(String key);
}
