package com.michael.chaos.authorization.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 注册客户端主键的确定性。
 *
 * <p>这条不变量错一次的表现是「每次重启授权服务器，所有人都得重新登录」：授权记录里存着
 * 签发时的 registeredClientId，主键随机的话重启后就反查不到客户端，令牌被判为 inactive。
 */
class RegisteredClientIdsTest {

    @Test
    void shouldDeriveTheSameIdForTheSameClientId() {
        assertThat(RegisteredClientIds.stableId("iam-client"))
                .isEqualTo(RegisteredClientIds.stableId("iam-client"));
    }

    @Test
    void shouldDeriveDifferentIdsForDifferentClients() {
        assertThat(RegisteredClientIds.stableId("iam-client"))
                .isNotEqualTo(RegisteredClientIds.stableId("order-client"));
    }

    @Test
    void shouldRejectBlankClientId() {
        assertThatThrownBy(() -> RegisteredClientIds.stableId(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegisteredClientIds.stableId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
