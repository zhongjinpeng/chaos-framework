package com.michael.chaos.test.security;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 测试用 {@link LoginUser} 构建器。
 *
 * <pre>{@code
 * LoginUser admin = TestLoginUsers.user("1001").tenant("tenant-a").roles("admin").build();
 * LoginUser reader = TestLoginUsers.user("1002").tenant("tenant-a").permissions("order:read").build();
 * }</pre>
 *
 * <p>只依赖 chaos-security-api，不需要 Spring Security。</p>
 */
public final class TestLoginUsers {

    private final String userId;

    private String username;

    private String tenantId = "";

    private final Set<String> roles = new LinkedHashSet<>();

    private final Set<String> permissions = new LinkedHashSet<>();

    private TestLoginUsers(String userId) {
        this.userId = userId;
        this.username = userId;
    }

    /**
     * 以用户 ID 开始构建，用户名默认与用户 ID 相同。
     */
    public static TestLoginUsers user(String userId) {
        return new TestLoginUsers(userId);
    }

    /**
     * 设置用户名。
     */
    public TestLoginUsers username(String username) {
        this.username = username;
        return this;
    }

    /**
     * 设置租户 ID。
     */
    public TestLoginUsers tenant(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * 追加角色编码（不带 {@code ROLE_} 前缀，与 JWT claim 保持一致）。
     */
    public TestLoginUsers roles(String... roles) {
        this.roles.addAll(List.of(roles));
        return this;
    }

    /**
     * 追加权限编码。
     */
    public TestLoginUsers permissions(String... permissions) {
        this.permissions.addAll(List.of(permissions));
        return this;
    }

    /**
     * 构建不可变的 LoginUser。
     */
    public LoginUser build() {
        return new LoginUser(userId, username, tenantId, roles, permissions);
    }
}
