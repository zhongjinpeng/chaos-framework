package com.michael.chaos.gateway.nacos;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.util.StringUtils;

/**
 * 解析 Nacos 中的 Gateway 路由定义。
 */
public class NacosRouteDefinitionParser {

    private final ObjectMapper objectMapper;

    public NacosRouteDefinitionParser() {
        this(new ObjectMapper(new YAMLFactory())
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    NacosRouteDefinitionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析配置内容。
     *
     * @param content Nacos 配置内容
     * @return 路由定义列表
     */
    public List<RouteDefinition> parse(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        try {
            RouteDefinitionDocument document = objectMapper.readValue(content, RouteDefinitionDocument.class);
            List<RouteDefinition> routes = document.getRoutes();
            if (routes == null) {
                routes = List.of();
            }
            validate(routes);
            return List.copyOf(routes);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse Nacos gateway routes", ex);
        }
    }

    private void validate(List<RouteDefinition> routes) {
        Set<String> routeIds = new LinkedHashSet<>();
        for (RouteDefinition route : routes) {
            if (route == null) {
                throw new IllegalArgumentException("Gateway route must not be null");
            }
            if (!StringUtils.hasText(route.getId())) {
                throw new IllegalArgumentException("Gateway route id must not be blank");
            }
            if (!routeIds.add(route.getId())) {
                throw new IllegalArgumentException("Duplicate gateway route id: " + route.getId());
            }
            if (route.getUri() == null) {
                throw new IllegalArgumentException("Gateway route uri must not be null: " + route.getId());
            }
            if (route.getMetadata() == null) {
                route.setMetadata(new LinkedHashMap<>());
            }
            validatePredicates(route);
            validateFilters(route);
        }
    }

    private void validatePredicates(RouteDefinition route) {
        List<PredicateDefinition> predicates = route.getPredicates();
        if (predicates == null || predicates.isEmpty()) {
            throw new IllegalArgumentException("Gateway route predicates must not be empty: " + route.getId());
        }
        for (PredicateDefinition predicate : predicates) {
            if (predicate == null || !StringUtils.hasText(predicate.getName())) {
                throw new IllegalArgumentException("Gateway route predicate name must not be blank: " + route.getId());
            }
            if (predicate.getArgs() == null) {
                predicate.setArgs(new LinkedHashMap<>());
            }
        }
    }

    private void validateFilters(RouteDefinition route) {
        List<FilterDefinition> filters = route.getFilters();
        if (filters == null) {
            route.setFilters(new ArrayList<>());
            return;
        }
        for (FilterDefinition filter : filters) {
            if (filter == null || !StringUtils.hasText(filter.getName())) {
                throw new IllegalArgumentException("Gateway route filter name must not be blank: " + route.getId());
            }
            if (filter.getArgs() == null) {
                filter.setArgs(new LinkedHashMap<>());
            }
        }
    }

    static class RouteDefinitionDocument {

        private List<RouteDefinition> routes = new ArrayList<>();

        public List<RouteDefinition> getRoutes() {
            return routes;
        }

        public void setRoutes(List<RouteDefinition> routes) {
            this.routes = routes == null ? new ArrayList<>() : routes;
        }
    }
}
