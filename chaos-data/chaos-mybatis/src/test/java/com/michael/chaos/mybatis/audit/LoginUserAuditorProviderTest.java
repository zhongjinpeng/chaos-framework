package com.michael.chaos.mybatis.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 基于登录用户 SPI 的操作人提供者测试。
 */
class LoginUserAuditorProviderTest {

    /**
     * 已登录时使用登录用户 ID。
     */
    @Test
    void shouldUseLoginUserId() {
        LoginUserAuditorProvider provider = new LoginUserAuditorProvider(
                () -> Optional.of(new LoginUser("1001", "alice", "tenant-a", Set.of(), Set.of())),
                () -> "fallback");

        assertThat(provider.currentAuditor()).isEqualTo("1001");
    }

    /**
     * 未登录（定时任务、消息消费）时回退到备用提供者。
     */
    @Test
    void shouldFallbackWhenNoLoginUser() {
        LoginUserAuditorProvider provider = new LoginUserAuditorProvider(Optional::empty, () -> "system");

        assertThat(provider.currentAuditor()).isEqualTo("system");
    }
}
