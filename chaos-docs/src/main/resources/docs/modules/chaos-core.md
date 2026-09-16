# chaos-core

## 职责

基础常量、请求上下文、统一上下文传播、统一异常和错误码、幂等与分布式锁契约、IP/CIDR 匹配工具。

## 依赖方式

直接依赖，通常由其他 starter 间接引入。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-core</artifactId>
</dependency>
```

## 配置

无。

## 请求上下文与标识符校验

`RequestContextSnapshot` 在构造时对 `tenantId`、`userId` 执行白名单校验（`ContextIdentifiers`）：

- 允许字符：字母、数字以及 `_ - . : @`，长度 1~128；
- 非法值（引号、空白、换行、中文、超长等）统一规范化为空字符串，并记录一条不含原文的 WARN 日志。

该校验是所有入口（HTTP 请求头、MQ 消息头、认证结果、业务代码 `RequestContext.setTenantId`）的统一闸门，
下游 MyBatis 租户 SQL 改写、MDC、出站请求头拿到的值不可能包含 SQL/日志/响应头注入字符。
白名单刻意不可配置，业务如需其他格式，应在认证层映射为内部 ID。

## 上下文传播

```java
ContextSnapshot snapshot = ContextPropagation.capture();
executor.execute(ContextPropagation.wrap(task));

// 请求边界结束时兜底清理所有注册的 ThreadLocal
ContextPropagation.clearAll();
```

- `ContextAccessor#clear()`：请求结束时的清理钩子，默认等价于恢复空快照；
- 捕获时基于同一份访问器数组，避免并发注册导致数组越界；
- 恢复过程中某个访问器失败时，已恢复的作用域会被逆序关闭，不残留半份上下文。

## 幂等契约

- `IdempotentKeyContext` 新增 `tenantId`、`userId` 维度；5 参数构造器已废弃。
- `DefaultIdempotentKeyGenerator` 生成 `idem:{method}:{path}:t={tenant}:u={user}:{key}`，缺少 key 时返回空字符串，不再用 traceId 兜底。
- `IdempotentRepository#remove` 默认空实现仅为兼容；支持失败重试的实现（如 Redis）必须覆盖。
- `InMemoryIdempotentRepository` 容量满时淘汰最早写入的 key，过期清理摊销执行，不再每次全量扫描。
- `IdempotentRecordStore` / `IdempotentRecord` 保存首次响应快照，供重复请求回放（见
  [幂等能力文档](../capabilities/idempotency.md)）。与 `IdempotentRepository` 分工：
  仓储回答"key 是否被占用"，快照存储回答"第一次的结果是什么"，拆开是为了让只需拒绝重复的调用方
  （如 MQ 消费幂等）不必承担存储响应体的成本。
- `IdempotentRecordCodec` 定义唯一的快照线格式（带版本号的单行文本，无序列化框架依赖），
  所有 `IdempotentRecordStore` 实现共用；解不出来时返回 `null`，调用方按"没有快照"处理。

## 网络工具

```java
CidrMatcher trusted = CidrMatcher.of(List.of("10.0.0.0/8", "fd00::/8"));
trusted.matches("10.1.2.3"); // true
```

只接受字面量 IP，不做 DNS 解析；配置非法时启动期抛出 `IllegalArgumentException`。

## 错误码

新增 `METHOD_NOT_ALLOWED("405")`、`UNSUPPORTED_MEDIA_TYPE("415")`。

```java
throw new BizException(CommonErrorCode.BAD_REQUEST, "invalid input");
```

## 注意事项

- 本模块不得依赖 Spring、Servlet 或任何基础设施 SDK（架构测试强制）。
- 新增公共 API 后同步更新本手册和 architecture.md。
