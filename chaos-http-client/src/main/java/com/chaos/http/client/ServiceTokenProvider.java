package com.chaos.http.client;

/**
 * 服务间调用使用的短期令牌。
 *
 * <p>实现负责缓存与刷新，调用方只在请求头中使用返回的令牌，不记录日志。</p>
 */
public interface ServiceTokenProvider {

    /**
     * 获取当前有效令牌；必要时同步刷新。
     *
     * @return 服务访问令牌
     */
    String token();

    /**
     * 丢弃缓存令牌，下一次 {@link #token()} 重新申请。
     */
    void invalidate();
}
