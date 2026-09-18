package com.michael.chaos.security.redis.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 存量数据与当前类不兼容时的读取行为测试。
 *
 * <p>框架升级改动参与序列化的类之后，Redis 里的旧记录会在反序列化时抛异常。期望的处置是
 * 「删键 + 当作不存在」，让调用方走令牌无效分支；若异常上抛，一条读不动的旧记录会把请求打成 500，
 * 且只能靠人工清 Redis 恢复。</p>
 */
class StaleValueReaderTest {

    private static final String AUTHORIZATION_KEY = "chaos:authorization:authorization:auth-1";

    /**
     * 授权对象反序列化失败时应返回 null 并删除该键。
     */
    @Test
    void shouldDropIncompatibleAuthorizationInsteadOfPropagating() {
        ExplodingRedisTemplate redisTemplate = new ExplodingRedisTemplate();
        RedisOAuth2AuthorizationService service =
                new RedisOAuth2AuthorizationService(redisTemplate, new ChaosAuthorizationProperties());

        assertThatCode(() -> assertThat(service.findById("auth-1")).isNull()).doesNotThrowAnyException();
        assertThat(redisTemplate.deletedKeys()).containsExactly(AUTHORIZATION_KEY);
    }

    /**
     * 客户端注册信息反序列化失败时同样应删键并返回 null，启动时会重新写入默认客户端。
     */
    @Test
    void shouldDropIncompatibleRegisteredClient() {
        ExplodingRedisTemplate redisTemplate = new ExplodingRedisTemplate();
        RedisRegisteredClientRepository repository =
                new RedisRegisteredClientRepository(redisTemplate, new ChaosAuthorizationProperties());

        assertThat(repository.findById("client-1")).isNull();
        assertThat(redisTemplate.deletedKeys()).hasSize(1);
    }

    /**
     * 任何一次 get 都抛 {@link SerializationException} 的模板，模拟存量数据与当前类不兼容。
     */
    private static final class ExplodingRedisTemplate extends RedisTemplate<Object, Object> {

        private final List<Object> deletedKeys = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<Object, Object> opsForValue() {
            return (ValueOperations<Object, Object>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("get".equals(method.getName())) {
                            throw new SerializationException(
                                    "Cannot deserialize",
                                    new java.io.InvalidClassException("local class incompatible")
                            );
                        }
                        return null;
                    }
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public SetOperations<Object, Object> opsForSet() {
            return (SetOperations<Object, Object>) Proxy.newProxyInstance(
                    SetOperations.class.getClassLoader(),
                    new Class<?>[]{SetOperations.class},
                    (proxy, method, args) -> null
            );
        }

        @Override
        public Boolean delete(Object key) {
            deletedKeys.add(key);
            return true;
        }

        private List<Object> deletedKeys() {
            return deletedKeys;
        }
    }
}
