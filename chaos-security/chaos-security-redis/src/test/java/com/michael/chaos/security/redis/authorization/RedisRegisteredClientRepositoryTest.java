package com.michael.chaos.security.redis.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.core.RegisteredClientIds;
import com.michael.chaos.test.redis.InMemoryRedisTemplates;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * Redis 客户端仓储。
 *
 * <p>用一个内存 Map 假扮 Redis：这里要验的是键的组织方式与孤儿清理，不是 Redis 本身。
 */
class RedisRegisteredClientRepositoryTest {

    private Map<Object, Object> store;

    private RedisRegisteredClientRepository repository;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        repository = new RedisRegisteredClientRepository(
                InMemoryRedisTemplates.create(store), new ChaosAuthorizationProperties());
    }

    @Test
    void shouldRoundTripByIdAndByClientId() {
        RegisteredClient client = client(RegisteredClientIds.stableId("iam-client"), "iam-client");

        repository.save(client);

        assertThat(repository.findById(client.getId()).getClientId()).isEqualTo("iam-client");
        assertThat(repository.findByClientId("iam-client").getId()).isEqualTo(client.getId());
    }

    @Test
    void shouldSurviveARestartWithTheSameStableId() {
        // 重启 = 新建一个仓储对象读同一份 Redis 数据。主键由 clientId 推导，
        // 所以新进程仍然能按授权记录里存的 registeredClientId 反查到客户端。
        String stableId = RegisteredClientIds.stableId("iam-client");
        repository.save(client(stableId, "iam-client"));

        RedisRegisteredClientRepository afterRestart = new RedisRegisteredClientRepository(
                InMemoryRedisTemplates.create(store), new ChaosAuthorizationProperties());

        assertThat(afterRestart.findById(stableId)).isNotNull();
    }

    @Test
    void shouldDropTheStaleRecordWhenTheSameClientIdGetsANewId() {
        repository.save(client("old-id", "iam-client"));

        repository.save(client("new-id", "iam-client"));

        // 旧主键那条要清掉，否则会留下一条永远读不到的孤儿记录。
        assertThat(repository.findById("old-id")).isNull();
        assertThat(repository.findByClientId("iam-client").getId()).isEqualTo("new-id");
    }

    @Test
    void shouldReturnNullForUnknownLookups() {
        assertThat(repository.findById("missing")).isNull();
        assertThat(repository.findByClientId("missing")).isNull();
        assertThat(repository.findById(null)).isNull();
        assertThat(repository.findByClientId(" ")).isNull();
    }

    @Test
    void shouldRejectNullClient() {
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static RegisteredClient client(String id, String clientId) {
        return RegisteredClient.withId(id)
                .clientId(clientId)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("read")
                .build();
    }

}
