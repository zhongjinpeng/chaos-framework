# chaos-domain

## 职责

DDD 聚合、领域事件、分页请求和分页结果等纯模型。源码目录为 `chaos-foundation/chaos-domain`，只依赖 `chaos-core`。

> DDD 原语模块（目录 `chaos-foundation/chaos-domain`），
> Java 包 `com.michael.chaos.common.*` 更名为 `com.michael.chaos.domain.*`，其中原 `common.domain` 子包改为 `domain.model`
> （避免出现 `domain.domain`）。

| 包 | 类型 |
| --- | --- |
| `com.michael.chaos.domain.model` | `AggregateRoot`、`DomainEvent` |
| `com.michael.chaos.domain.dto` | `PageQuery`、`PageResult` |
| `com.michael.chaos.domain.cache` | `CacheKeyStrategy`、`DefaultCacheKeyStrategy` |

## 依赖方式

业务 domain/application 模块可直接依赖。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-domain</artifactId>
</dependency>
```

如果是业务应用，优先使用对应 starter；如果是 domain/application 纯接口依赖，才直接依赖功能模块。

## 配置

无。

## 示例

```java
PageResult<OrderDTO> page = new PageResult<>(records, total, query.pageNo(), query.pageSize());
long offset = query.offset();
```

## 分页边界

`PageQuery` 会规范化分页参数：

| 参数 | 规则 |
| --- | --- |
| `pageNo` | 小于 1 取 1，大于 `MAX_PAGE_NO`（10000）截断 |
| `pageSize` | 小于等于 0 取 20，大于 `MAX_PAGE_SIZE`（500）截断 |

限制页码是为了防止 `pageNo=100000000` 这类深分页让数据库扫描并丢弃海量行。需要遍历全量数据时使用按主键 seek 的游标分页。

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。

## 注意事项

- 保持模块职责单一，避免把无关能力塞入当前模块。
- 新增公共 API 后同步更新本手册和 architecture.md。
