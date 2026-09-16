# chaos-gateway-nacos

## 职责

为 Spring Cloud Gateway 提供基于 Nacos Config 的动态路由仓库和监听刷新能力。

## 依赖方式

业务网关优先引入 starter：

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-gateway-nacos-starter</artifactId>
</dependency>
```

如果只复用解析器、仓库或同步器，可以直接依赖 `chaos-gateway-nacos`。

## 配置

```yaml
chaos:
  gateway:
    nacos-routes:
      enabled: true
      data-id: chaos-gateway-routes.yaml
      group: DEFAULT_GROUP
      timeout-ms: 3000
      fail-fast: false
      clear-on-empty: false
```

Nacos 配置内容：

```yaml
routes:
  - id: order-service
    uri: lb://order-service
    predicates:
      - name: Path
        args:
          _genkey_0: /order/**
    filters:
      - name: StripPrefix
        args:
          _genkey_0: "1"
```

## 扩展点

- 覆盖 `NacosRouteDefinitionParser` 可支持企业自定义路由 DSL。
- 覆盖 `RouteDefinitionRepository` 可接入数据库、Redis 或配置平台，自动装配会退让。
- 覆盖 `NacosRouteDefinitionSynchronizer` 可接入审批、灰度发布或配置签名校验流程。

## 注意事项

- 该模块是 Gateway 的可选扩展，`chaos-gateway` 核心不依赖 Nacos SDK。
- 默认保留上一次有效路由，避免错误配置发布后立即清空线上路由。
- `fail-fast=true` 会在初始配置为空、Nacos 不可用或解析失败时阻止应用启动。
- `clear-on-empty=true` 会把空 Nacos 配置解释为清空动态路由，生产环境谨慎使用。
- 路由配置使用 Spring Cloud Gateway 原生 `RouteDefinition`，谓词和过滤器名称必须和 Gateway Factory 匹配。
