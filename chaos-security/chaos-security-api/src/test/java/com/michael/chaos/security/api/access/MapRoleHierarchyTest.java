package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 角色继承关系测试。
 */
class MapRoleHierarchyTest {

    /**
     * 应展开多层继承的全部角色。
     */
    @Test
    void shouldExpandTransitiveRoles() {
        RoleHierarchy hierarchy = new MapRoleHierarchy(Map.of(
                "admin", List.of("manager"),
                "manager", List.of("user")));

        assertThat(hierarchy.reachableRoles(Set.of("admin")))
                .containsExactlyInAnyOrder("admin", "manager", "user");
        assertThat(hierarchy.reachableRoles(Set.of("manager")))
                .containsExactlyInAnyOrder("manager", "user");
        assertThat(hierarchy.reachableRoles(Set.of("user"))).containsExactly("user");
    }

    /**
     * 未配置继承关系时应原样返回。
     */
    @Test
    void shouldReturnOriginalRolesWhenHierarchyIsEmpty() {
        assertThat(new MapRoleHierarchy(Map.of()).reachableRoles(Set.of("user"))).containsExactly("user");
        assertThat(new MapRoleHierarchy(null).reachableRoles(Set.of("user"))).containsExactly("user");
        assertThat(RoleHierarchy.none().reachableRoles(Set.of("user", " "))).containsExactly("user");
    }

    /**
     * 存在环时应抛出可操作的诊断异常。
     */
    @Test
    void shouldRejectCycle() {
        assertThatThrownBy(() -> new MapRoleHierarchy(Map.of(
                "admin", List.of("manager"),
                "manager", List.of("admin"))))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("角色继承配置存在环")
                .hasMessageContaining("role-hierarchy");
    }

    /**
     * 自己继承自己同样是环。
     */
    @Test
    void shouldRejectSelfCycle() {
        assertThatThrownBy(() -> new MapRoleHierarchy(Map.of("admin", List.of("admin"))))
                .isInstanceOf(ChaosDiagnosticException.class);
    }

    /**
     * 空角色集合应原样返回。
     */
    @Test
    void shouldReturnEmptyForBlankRoles() {
        RoleHierarchy hierarchy = new MapRoleHierarchy(Map.of("admin", List.of("user")));
        assertThat(hierarchy.reachableRoles(Set.of())).isEmpty();
        assertThat(hierarchy.reachableRoles(null)).isEmpty();
    }
}
