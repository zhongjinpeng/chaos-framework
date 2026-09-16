package com.michael.chaos.authorization.core;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 由 {@code clientId} 推导 {@code RegisteredClient} 主键。
 *
 * <p>存在的理由是一个很容易踩、又极难自查的组合缺陷：授权记录（{@code OAuth2Authorization}）
 * 持久化在 Redis/JDBC 里，而客户端注册信息只活在内存里，且主键用 {@code UUID.randomUUID()}
 * 每次启动重新生成。于是：
 *
 * <pre>
 *   重启后 introspect(token)
 *     → findByToken 命中（授权记录还在、也没过期）
 *     → registeredClientRepository.findById(上次那个随机主键)
 *     → 找不到 → 令牌被判为 inactive → 401
 * </pre>
 *
 * <p>refresh_token 换令牌走同一条反查，所以刷新也救不回来 —— 表现是「每次重启授权服务器，
 * 所有人都得重新登录」，而排查时会一直怀疑存储侧丢了数据。
 *
 * <p>主键改成由 {@code clientId} 确定性推导之后，同一份配置在任何实例、任何一次启动上都得到
 * 同一个值，令牌自然跨重启存活。
 *
 * @author michael
 */
public final class RegisteredClientIds {

    /** 拼进摘要的命名空间，避免与其它按 clientId 生成的 UUID 撞上。 */
    private static final String NAMESPACE = "chaos:authorization:registered-client:";

    private RegisteredClientIds() {
    }

    /**
     * 由 clientId 推导确定性主键。
     *
     * @param clientId OAuth2 客户端标识
     * @return 稳定的 {@code RegisteredClient} 主键
     */
    public static String stableId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        return UUID.nameUUIDFromBytes((NAMESPACE + clientId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
