package com.michael.chaos.security.api.token;

import java.time.Duration;

/**
 * 默认空 JWT 撤销服务。
 *
 * <p>只用于本地开发或未启用撤销检查的场景；生产环境由生产安全检查阻断，避免注销、踢人下线静默失效。</p>
 */
public class NoopJwtRevocationService implements JwtRevocationService {

    /**
     * 默认不撤销任何 token。
     */
    @Override
    public boolean isRevoked(String tokenId) {
        return false;
    }

    /**
     * 默认不执行撤销。
     */
    @Override
    public void revoke(String tokenId, Duration ttl) {
    }
}
