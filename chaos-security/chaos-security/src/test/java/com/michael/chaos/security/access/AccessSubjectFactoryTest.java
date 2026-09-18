package com.michael.chaos.security.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 访问主体工厂测试。
 */
class AccessSubjectFactoryTest {

    private static final LoginUser USER =
            new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of("order:read"));

    /**
     * 默认解析器应沿用令牌里的权限。
     */
    @Test
    void shouldUseClaimsPermissionsByDefault() {
        AccessSubject subject = new AccessSubjectFactory(null).apply(USER);

        assertThat(subject.userId()).isEqualTo("1001");
        assertThat(subject.tenantId()).isEqualTo("tenant-a");
        assertThat(subject.roles()).containsExactly("user");
        assertThat(subject.permissions()).containsExactly("order:read");
        assertThat(subject.attributes()).isEmpty();
    }

    /**
     * 自定义解析器应覆盖令牌里的权限。
     */
    @Test
    void shouldOverridePermissionsWithResolver() {
        AccessSubject subject = new AccessSubjectFactory(user -> Set.of("order:*")).apply(USER);

        assertThat(subject.permissions()).containsExactly("order:*");
    }

    /**
     * 解析器返回 null 时回退到令牌权限。
     */
    @Test
    void shouldFallBackWhenResolverReturnsNull() {
        AccessSubject subject = new AccessSubjectFactory(user -> null).apply(USER);

        assertThat(subject.permissions()).containsExactly("order:read");
    }

    /**
     * 多个主体属性解析器应按顺序合并，后者覆盖前者。
     */
    @Test
    void shouldMergeSubjectAttributesInOrder() {
        AccessSubjectFactory factory = new AccessSubjectFactory(
                null,
                Arrays.asList(
                        user -> Map.of("department", "sales", "level", 1),
                        user -> Map.of("level", 3),
                        null));

        AccessSubject subject = factory.apply(USER);

        assertThat(subject.attributes())
                .containsEntry("department", "sales")
                .containsEntry("level", 3);
    }

    /**
     * 未登录时返回匿名主体。
     */
    @Test
    void shouldReturnAnonymousForNullUser() {
        assertThat(new AccessSubjectFactory(null).apply(null)).isEqualTo(AccessSubject.ANONYMOUS);
    }
}
