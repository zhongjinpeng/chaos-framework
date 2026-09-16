# 缓存 key 策略（chaos-domain + chaos-redis）

> 仓库中不存在 `chaos-cache` 模块。缓存 key 策略接口位于 `chaos-domain`（包 `com.michael.chaos.domain.cache`），带前缀的实现位于 `chaos-redis`。

## 组成

| 类型 | 位置 | 说明 |
| --- | --- | --- |
| `CacheKeyStrategy` | chaos-domain | 生成缓存 key 的抽象 |
| `DefaultCacheKeyStrategy` | chaos-domain | `namespace:key` |
| `PrefixedCacheKeyStrategy` | chaos-redis | 在委托策略结果前加 `chaos.redis.key-prefix` |

## 配置

引入 `chaos-redis-starter` 后默认注册 `PrefixedCacheKeyStrategy(DefaultCacheKeyStrategy, chaos.redis.key-prefix)`。

```yaml
chaos:
  redis:
    key-prefix: ${spring.application.name}
```

## 示例

```java
String key = cacheKeyStrategy.build("order", orderId); // order-service:order:1001
```

## 扩展点

- 注册自定义 `CacheKeyStrategy` Bean 覆盖默认实现，例如追加租户维度。
