package com.michael.chaos.security.api.access;

import java.util.Set;

/**
 * 角色继承关系。
 *
 * <p>把主体直接持有的角色展开为包含继承角色在内的完整角色集合，
 * 例如 {@code admin > manager > user} 时，持有 {@code admin} 的主体同样命中 {@code user} 的授权规则。</p>
 */
@FunctionalInterface
public interface RoleHierarchy {

    /**
     * 展开角色集合，返回包含自身与全部继承角色的结果。
     */
    Set<String> reachableRoles(Set<String> roles);

    /**
     * 返回不做任何展开的空继承关系。
     */
    static RoleHierarchy none() {
        return roles -> AccessCollections.copyStrings(roles);
    }
}
