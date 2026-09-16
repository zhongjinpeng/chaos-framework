package com.michael.chaos.test.redis;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 用内存 Map 假扮 Redis 的 {@link RedisTemplate}，供自动装配与仓储单元测试使用。
 *
 * <p>装配测试只关心「装了哪个实现、种子客户端有没有写进去」，不需要真连 Redis；
 * 真实读写行为由各仓储自己的单测覆盖。</p>
 *
 * <p>只模拟了 {@code opsForValue().set/get} 与 {@code delete}，其余操作返回 {@code null}。
 * 需要 Lua 脚本、过期时间、Hash/Set 等真实语义时，请使用 {@code ChaosContainers.redis()} 启动真实 Redis。</p>
 */
public final class InMemoryRedisTemplates {

    private InMemoryRedisTemplates() {
    }

    /**
     * 创建使用独立 backing map 的 RedisTemplate。
     */
    public static RedisTemplate<Object, Object> create() {
        return create(new HashMap<>());
    }

    /** 复用同一份 backing map 可以模拟「重启后读同一份 Redis 数据」。 */
    @SuppressWarnings("unchecked")
    public static RedisTemplate<Object, Object> create(Map<Object, Object> backing) {
        return new RedisTemplate<>() {
            @Override
            public ValueOperations<Object, Object> opsForValue() {
                return (ValueOperations<Object, Object>) Proxy.newProxyInstance(
                        ValueOperations.class.getClassLoader(),
                        new Class<?>[] {ValueOperations.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "set" -> {
                                backing.put(args[0], args[1]);
                                yield null;
                            }
                            case "get" -> backing.get(args[0]);
                            default -> null;
                        });
            }

            @Override
            public Boolean delete(Object key) {
                return backing.remove(key) != null;
            }

            @Override
            public void afterPropertiesSet() {
                // 真实实现会要求 RedisConnectionFactory；这里没有真连接，跳过初始化。
            }
        };
    }
}
