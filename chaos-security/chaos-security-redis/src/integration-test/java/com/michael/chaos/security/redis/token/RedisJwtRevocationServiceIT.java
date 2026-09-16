package com.michael.chaos.security.redis.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.token.JwtTokenIds;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis JWT 黑名单集成测试。
 *
 * <p>重点验证跨客户端互通：授权服务器通过 Spring Data Redis 写入的撤销记录，
 * 必须能被使用 Redisson 的服务按同一 key 读到（历史实现因 JDK 序列化 key 导致两侧互相不可见）。</p>
 */
@Testcontainers
class RedisJwtRevocationServiceIT {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    private static RedissonClient redissonClient;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    static void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * 写入后可查询，且 Redisson 以原始字符串 key 能读到同一条记录。
     */
    @Test
    void revocationShouldBeVisibleAcrossRedisClients() {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        RedisJwtRevocationService service = new RedisJwtRevocationService(template);
        String tokenId = JwtTokenIds.resolve("jwt-it-1", "token-value");

        service.revoke(tokenId, Duration.ofMinutes(5));

        assertThat(service.isRevoked(tokenId)).isTrue();
        assertThat(redissonClient.getBucket(RedisJwtRevocationService.key(tokenId), StringCodec.INSTANCE).isExists())
                .isTrue();
    }
}
