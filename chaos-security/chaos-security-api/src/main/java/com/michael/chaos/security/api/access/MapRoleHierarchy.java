package com.michael.chaos.security.api.access;

import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Map 配置的角色继承关系。
 *
 * <p>配置形如 {@code admin: [manager]}、{@code manager: [user]}，表示 admin 继承 manager，manager 继承 user。
 * 构造时一次性算好传递闭包，决策期只做一次查表。</p>
 */
public class MapRoleHierarchy implements RoleHierarchy {

    private final Map<String, Set<String>> closure;

    /**
     * 根据角色继承配置创建继承关系。
     *
     * @throws ChaosDiagnosticException 配置中存在环时抛出
     */
    public MapRoleHierarchy(Map<String, ? extends Collection<String>> hierarchy) {
        this.closure = buildClosure(normalize(hierarchy));
    }

    @Override
    public Set<String> reachableRoles(Set<String> roles) {
        Set<String> normalized = AccessCollections.copyStrings(roles);
        if (normalized.isEmpty() || closure.isEmpty()) {
            return normalized;
        }
        Set<String> reachable = new LinkedHashSet<>(normalized);
        for (String role : normalized) {
            reachable.addAll(closure.getOrDefault(role, Set.of()));
        }
        return Collections.unmodifiableSet(reachable);
    }

    private static Map<String, Set<String>> normalize(Map<String, ? extends Collection<String>> hierarchy) {
        if (hierarchy == null || hierarchy.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        hierarchy.forEach((role, inherited) -> {
            String normalizedRole = AccessCollections.normalizeString(role);
            if (normalizedRole.isBlank()) {
                return;
            }
            Set<String> normalizedInherited = AccessCollections.copyStrings(inherited);
            if (!normalizedInherited.isEmpty()) {
                normalized.put(normalizedRole, normalizedInherited);
            }
        });
        return normalized;
    }

    private static Map<String, Set<String>> buildClosure(Map<String, Set<String>> hierarchy) {
        if (hierarchy.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> closure = new LinkedHashMap<>();
        for (String role : hierarchy.keySet()) {
            closure.put(role, Collections.unmodifiableSet(expand(role, hierarchy)));
        }
        return Collections.unmodifiableMap(closure);
    }

    private static Set<String> expand(String role, Map<String, Set<String>> hierarchy) {
        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(hierarchy.getOrDefault(role, Set.of()));
        while (!pending.isEmpty()) {
            String current = pending.poll();
            if (current.equals(role)) {
                throw cycleDetected(role, current);
            }
            if (!reachable.add(current)) {
                continue;
            }
            pending.addAll(hierarchy.getOrDefault(current, Set.of()));
        }
        return reachable;
    }

    private static ChaosDiagnosticException cycleDetected(String role, String current) {
        return new ChaosDiagnosticException(new ChaosDiagnostic(
                "角色继承配置存在环：角色 " + role + " 通过 " + current + " 又回到了自己",
                List.of("chaos.security.access.role-hierarchy 中的角色互相继承，无法展开为有限的角色集合"),
                List.of(
                        "检查 chaos.security.access.role-hierarchy，去掉 " + role + " 与 " + current + " 之间的环",
                        "角色继承应当是自上而下的单向关系，例如 admin: [manager]、manager: [user]"
                )));
    }
}
