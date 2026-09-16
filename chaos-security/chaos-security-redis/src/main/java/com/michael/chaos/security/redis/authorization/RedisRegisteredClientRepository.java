package com.michael.chaos.security.redis.authorization;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 基于 Redis 的 OAuth2 客户端仓储。
 *
 * <p>与 {@link RedisOAuth2AuthorizationService} 配套：授权记录持久化了，客户端注册信息也必须
 * 持久化。两者不匹配时会出现「重启后全部令牌失效」——授权记录引用的 {@code registeredClientId}
 * 在新进程的内存仓库里不存在，令牌被判为 inactive。
 *
 * <p>存两类键：
 *
 * <ul>
 *   <li>{@code <前缀>:id:<主键>} → {@link RegisteredClient} 本体，供 {@code findById} 使用；
 *   <li>{@code <前缀>:client-id:<clientId>} → 主键，供 {@code findByClientId} 反查。
 * </ul>
 *
 * <p>客户端注册信息不设 TTL：它是配置而不是会话，过期没有意义。
 *
 * @author michael
 */
public class RedisRegisteredClientRepository implements RegisteredClientRepository {

    private final RedisTemplate<Object, Object> redisTemplate;

    private final String prefix;

    public RedisRegisteredClientRepository(
            RedisTemplate<Object, Object> redisTemplate,
            ChaosAuthorizationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.prefix = normalizePrefix(properties.getClient().getRedisKeyPrefix());
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        if (registeredClient == null) {
            throw new IllegalArgumentException("registeredClient must not be null");
        }
        // 同一个 clientId 换了主键时，先清掉旧主键那条记录，避免留下永远读不到的孤儿。
        Object previousId = redisTemplate.opsForValue().get(clientIdKey(registeredClient.getClientId()));
        if (previousId instanceof String existing && !existing.equals(registeredClient.getId())) {
            redisTemplate.delete(idKey(existing));
        }
        redisTemplate.opsForValue().set(idKey(registeredClient.getId()), registeredClient);
        redisTemplate.opsForValue().set(clientIdKey(registeredClient.getClientId()), registeredClient.getId());
    }

    @Override
    public RegisteredClient findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(idKey(id));
        return value instanceof RegisteredClient client ? client : null;
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        Object id = redisTemplate.opsForValue().get(clientIdKey(clientId));
        return id instanceof String resolved ? findById(resolved) : null;
    }

    private String idKey(String id) {
        return prefix + ":id:" + encode(id);
    }

    private String clientIdKey(String clientId) {
        return prefix + ":client-id:" + encode(clientId);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizePrefix(String value) {
        return value == null || value.isBlank()
                ? "chaos:authorization:client"
                : value.replaceAll(":+$", "");
    }
}
