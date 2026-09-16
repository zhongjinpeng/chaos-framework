package com.michael.chaos.gateway.nacos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 以 Nacos 配置为唯一写入源的 Gateway 路由仓库。
 */
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository {

    private final AtomicReference<Map<String, RouteDefinition>> routes = new AtomicReference<>(Map.of());

    /**
     * 原子替换当前动态路由。
     *
     * @param routeDefinitions 新路由定义
     */
    public void replaceRoutes(List<RouteDefinition> routeDefinitions) {
        Map<String, RouteDefinition> nextRoutes = new LinkedHashMap<>();
        for (RouteDefinition routeDefinition : routeDefinitions) {
            nextRoutes.put(routeDefinition.getId(), routeDefinition);
        }
        routes.set(Collections.unmodifiableMap(new LinkedHashMap<>(nextRoutes)));
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(routes.get().values());
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.error(new UnsupportedOperationException("Nacos gateway routes are read-only"));
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.error(new UnsupportedOperationException("Nacos gateway routes are read-only"));
    }
}
