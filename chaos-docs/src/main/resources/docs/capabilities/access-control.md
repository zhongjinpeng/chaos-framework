# 访问控制：RBAC + ABAC（chaos-security-api + chaos-security）

> 仓库中不存在 `chaos-rbac` / `chaos-abac` 模块。授权模型位于 `chaos-security-api`（包
> `com.michael.chaos.security.api.access`，不依赖 Spring），注解与切面位于 `chaos-security`，装配位于 `chaos-autoconfigure`。

## 组成

| 能力 | 位置 |
| --- | --- |
| 决策入口 `AuthorizationManager` / `DefaultAuthorizationManager` | chaos-security-api |
| RBAC 策略 `RbacAuthorizationPolicy`（admin 角色、权限编码、角色继承、权限通配） | chaos-security-api |
| ABAC 策略 `AbacAuthorizationPolicy`、属性模型 `AttributeCondition` / `AttributeOperator` / `AttributeReference` | chaos-security-api |
| 配置形态策略 `PolicyDefinition` / `ConditionDefinition` / `AccessPolicyFactory` | chaos-security-api |
| 策略分组 `CompositeAuthorizationPolicy`、合并算法 `PolicyCombiningAlgorithm` | chaos-security-api |
| 角色继承 `RoleHierarchy` / `MapRoleHierarchy`、权限通配 `PermissionPatterns` | chaos-security-api |
| 动态策略来源 SPI `AuthorizationPolicySource`、缓存装饰器 `CachingAuthorizationPolicySource`、策略 JSON 编解码 `AuthorizationPolicyJsonCodec` | chaos-security-api |
| 环境属性 SPI `AuthorizationContextContributor`、组合实现 `CompositeAuthorizationContextContributor` | chaos-security-api |
| 装配构造器 `AuthorizationManagerBuilder`（资源服务器与网关共用同一套拼装规则） | chaos-security-api |
| Redis 动态策略 `RedisAuthorizationPolicySource` | chaos-security-redis |
| 网关粗粒度鉴权 `AccessControlGatewayFilter` | chaos-gateway |
| 注解 `@Permission`（只看权限编码）、`@RequireAccess` + `@AccessAttribute`（动作 + 资源 + 属性） | chaos-security |
| 切面 `PermissionAspect`、`RequireAccessAspect`，表达式求值 `AccessExpressionEvaluator` | chaos-security |
| 主体构造 `AccessSubjectFactory`、权限补全 `PermissionResolver`、主体属性 `SubjectAttributeResolver` | chaos-security |
| 环境属性 `TimeContextContributor`、`RequestContextContributor`、`ServletRequestContextContributor` | chaos-security |

引入方式：`chaos-security-starter`（或场景 starter `chaos-web-service-starter`）即可，无需额外依赖。

## 决策模型

一次授权决策的输入是 `AuthorizationRequest`：

| 部分 | 内容 | ABAC 引用方式 |
| --- | --- | --- |
| 主体 `AccessSubject` | userId、username、tenantId、roles、permissions、主体属性 | `subject.tenantId`、`subject.roles`、`subject.<自定义属性>` |
| 动作 `action` | 权限编码，如 `order:update` | 策略的 `actions` |
| 资源 `AuthorizationResource` | type、id、资源属性 | `resource.type`、`resource.id`、`resource.<自定义属性>` |
| 环境 `environment` | 时间、链路、IP、HTTP 信息 | `environment.time`、`environment.clientIp`、`environment.http.method` |

策略执行顺序与合并规则：

1. 配置文件里的 ABAC 策略被打包成一条 `CompositeAuthorizationPolicy`，组内按
   `chaos.security.access.combining-algorithm` 合并（默认拒绝优先）；
2. 组合策略与 `RbacAuthorizationPolicy` 等策略 Bean 之间**恒为拒绝优先**，任何 DENY 策略都能否决 RBAC 的放行；
3. 所有策略都弃权时默认拒绝。

因此细粒度限制（"只能改自己的订单"、"外网禁止导出"）要写成 **DENY 策略**：命中即拒绝，未命中时交回 RBAC 判定。
写成 ALLOW 策略在默认算法下是无效的——RBAC 已经放行，再多一条 ALLOW 不改变结果。

## RBAC

权限与角色来自令牌声明（`LoginUser`）。两项增强默认行为如下：

```yaml
chaos:
  security:
    access:
      admin-roles: [admin]            # 命中即放行
      wildcard-permission-enabled: true  # order:* 覆盖 order:read、order:read:self
      role-hierarchy:
        admin: [manager]
        manager: [user]
```

- 权限通配按 `:` 分段：`*` 匹配全部；末段 `*` 吃掉剩余全部分段；中间 `*` 只吃一段（`order:*:self`）。
- 角色继承在启动时算好传递闭包，配置成环会直接启动失败并指出环在哪。

令牌里只放角色、权限在服务端展开时，提供一个 `PermissionResolver` Bean：

```java
@Bean
PermissionResolver permissionResolver(RolePermissionCache cache) {
    return user -> cache.permissionsOf(user.roles());
}
```

它同时作用于 `@Permission`、`@RequireAccess` 和 `SecurityUtils.hasPermission`。

## ABAC

### 在方法上声明资源

```java
@RequireAccess(
        action = "order:update",
        resourceType = "order",
        resourceId = "#order.id",
        attributes = {
                @AccessAttribute(name = "ownerId", value = "#order.ownerId"),
                @AccessAttribute(name = "amount", value = "#order.amount")
        })
public void update(OrderDTO order) { ... }
```

`resourceId` 与 `@AccessAttribute.value` 是 SpEL，可以引用方法参数（`#order`、`#p0`）、当前登录用户（`#user`）
和目标 Bean（`#root.target`）；字面量要加引号（`"'fixed-id'"`）。表达式写错时抛出诊断异常，消息里带方法位置与可用变量。

### 在配置里声明策略

```yaml
chaos:
  security:
    access:
      policies:
        - id: order-owner-only
          description: 只能修改自己的订单
          effect: DENY
          actions: [order:update]
          conditions:
            - left: resource.ownerId
              operator: NOT_EQ
              right: subject.userId
        - id: deny-large-amount-outside-office-hours
          effect: DENY
          actions: [order:update]
          conditions:
            - left: resource.amount
              operator: GT
              values: ["100000"]
            - left: environment.time
              operator: BETWEEN
              values: ["18:00:00", "23:59:59"]
```

一条策略里的多个 `conditions` 是**与**关系；要表达"或"就写多条策略。

### 操作符

| 操作符 | 说明 |
| --- | --- |
| `EQ` / `NOT_EQ` | 等值比较，`right` 可以指向另一个属性 |
| `IN` / `NOT_IN` | 属于固定值集合；左侧是集合时表示交集非空 |
| `EXISTS` / `NOT_EXISTS` | 属性是否存在，不需要比较值 |
| `GT` / `GTE` / `LT` / `LTE` | 按数值或时间比较，只支持一个比较值 |
| `BETWEEN` | 闭区间，`values` 必须是两个值，与先后顺序无关 |
| `REGEX` | 完整匹配（不是 find），非法正则在启动期报错 |
| `CONTAINS` | 集合包含全部指定值；字符串包含全部子串 |

比较规则：依次尝试数值、时间戳、日期时间、日期、时间，都不适用时按字符串比较。
只有一侧能解析为数值/时间时视为类型不兼容，条件不命中——避免出现 `"abc" > "100"` 这种反直觉的放行。

### 环境属性

| 属性 | 来源 |
| --- | --- |
| `now`、`date`、`time`、`hour`、`dayOfWeek` | `TimeContextContributor` |
| `traceId`、`tenantId`、`userId`、`appName` | `RequestContextContributor`（`RequestContext`） |
| `clientIp`、`uri` | `RequestContextContributor`（MDC，由 chaos-web 按可信代理解析后写入） |
| `http.method`、`http.path` | `ServletRequestContextContributor`（Servlet 环境） |

框架内置的属性名都定义在 `AccessEnvironment` 常量类里，资源服务器与网关共用同一份——写代码时引用常量，
写配置时用表格里的名字。

补充自定义环境属性：注册一个 `AuthorizationContextContributor` Bean。
补充自定义主体属性（部门、职级、数据归属地）：注册一个 `SubjectAttributeResolver` Bean，实现方负责缓存。

## 配置错误的反馈

策略配置在启动时全部校验，出错抛 `ChaosDiagnosticException`，消息里直接给出配置路径与改法，例如：

```
问题：授权策略 order-owner-only 的 IN 条件缺少比较值
原因：
  - chaos.security.access.policies[0].conditions[0] 既没有 values 也没有 right
怎么修：
  - 补充 chaos.security.access.policies[0].conditions[0].values（固定值），或补充 …right（另一个属性引用）
```

被校验的情况：策略缺 ID、ID 重复、既无 actions 又无 conditions（会对所有请求生效）、
操作符与比较值不匹配、`right` 与 `values` 同时配置、`BETWEEN` 边界值不是两个、正则非法、属性引用为空。

## 可观测性

- 拒绝时上报 `chaos.security.access.denied`，标签 `source=permission`（`@Permission`）或 `source=require-access`（`@RequireAccess`）。
- 拒绝时发布审计事件 `security.permission.denied`，属性含 `action`、命中的 `policy`、`resourceType`、`resourceId`。
- 启动报告与 `/actuator/chaos` 的 `security` 一节展示 `access.policies`（策略条数）、`access.combining-algorithm`、
  `access.wildcard-permission`、`access.admin-roles`、`access.policy-source`；`gateway` 一节展示
  `access.enabled`、`access.rules`、`access.policies`。
- 网关拒绝时上报同一个指标，标签 `source=gateway`。

## 动态策略：把策略放到 Redis

策略调通后可以搬到 Redis，改策略不重启：

```yaml
chaos:
  security:
    access:
      policy-source: redis                                # 默认 config
      policy-redis-key: chaos:security:access:policies     # 默认值
      policy-cache-ttl: 30s                                # 也是策略生效的最大延迟
```

Redis 中存的是与 `policies` 结构一致的 JSON 数组，可以直接用 redis-cli 查看和修改：

```bash
redis-cli set chaos:security:access:policies '[
  {"id":"deny-external-network","effect":"DENY","actions":["order:read"],
   "conditions":[{"left":"environment.network","operator":"EQ","values":["external"]}]}
]'
```

- 枚举支持 `NOT_EQ`、`not-eq`、`notEq` 三种写法，与 YAML 保持一致；`enabled` 不写视为启用。
- 配置里的策略与 Redis 里的策略同时生效，互不覆盖。
- **拉取失败时继续使用上一份快照**并打告警日志：策略是拒绝规则的载体，让它因为一次网络抖动或一次写坏的
  JSON 而清空，等于悄悄放行。只有启动后第一次拉取就失败（没有任何快照可用）才会抛异常。
- 想换成数据库或配置中心：实现 `AuthorizationPolicySource` 并注册为 Bean，用 `AccessPolicyFactory`
  把记录转成策略即可，其余装配不用动。持有 `CachingAuthorizationPolicySource` Bean 可以调用 `refresh()` 立即失效缓存。

## 网关粗粒度鉴权

网关侧只依赖 `chaos-security-api`，用同一套策略模型做"整类接口进不进得来"的判断：

```yaml
chaos:
  gateway:
    access:
      enabled: true                 # 默认关闭
      rules:
        - path: /api/orders/**
          methods: [GET]
          action: order:read
          resource-type: order
        - path: /api/admin/**
          action: admin:access
      policies:
        - id: deny-write-from-external
          effect: deny
          actions: [order:write]
          conditions:
            - left: environment.clientIp
              operator: regex
              values: ["(?!10\\.).*"]
```

- 规则按顺序匹配，第一条命中的生效；**没有命中任何规则的请求直接放行**——网关规则是额外加固，不是唯一防线，
  漏配一条不该把整个站点挡死。
- 白名单路径（`chaos.gateway.whitelist`）不做鉴权；路径穿越、歧义编码的请求不参与规则匹配。
- 主体来自 token 解析结果（JWT claim 或 introspection 结果中的 `roles`、`permissions`），不读任何请求头。
- 环境属性可用 `clientIp`、`http.method`、`http.path`、`uri`、`tenantId`。
- 边界：网关看不到请求体，也不该为了鉴权去查业务数据。"只能改自己的订单"这类判断仍由下游服务的
  `@RequireAccess` 完成。

## 扩展点一览

| 想做的事 | 做法 |
| --- | --- |
| 权限在服务端按角色展开 | 提供 `PermissionResolver` Bean |
| ABAC 需要部门、职级等主体属性 | 提供 `SubjectAttributeResolver` Bean |
| 需要额外的环境属性 | 提供 `AuthorizationContextContributor` Bean |
| 策略存在 Redis | 配置 `chaos.security.access.policy-source=redis` |
| 策略存在数据库或配置中心 | 提供 `AuthorizationPolicySource` Bean（用 `AccessPolicyFactory` 把记录转成策略） |
| 网关按路由鉴权 | 配置 `chaos.gateway.access.enabled=true` 与 `rules` |
| 完全自定义决策逻辑 | 提供 `AuthorizationPolicy` Bean，或直接覆盖 `AuthorizationManager` Bean |
