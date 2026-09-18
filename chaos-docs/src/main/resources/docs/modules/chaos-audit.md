# chaos-audit

## 职责

提供统一审计事件模型、审计发布端口和默认结构化日志发布器。源码目录为 `chaos-audit/chaos-audit`，对外 artifactId 为 `chaos-audit`。

## 依赖方式

业务应用优先通过 `chaos-audit-starter`、`chaos-security-starter`、`chaos-authorization-starter` 或 `chaos-gateway-starter` 间接引入。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-audit</artifactId>
</dependency>
```

## 配置

```yaml
chaos:
  audit:
    enabled: true
```

关闭审计时会注册 `NoopAuditEventPublisher`，避免调用方做空判断。

## 默认输出

默认发布器把审计事件写到独立的 `AUDIT_LOG` logger。生产环境建议在 logback 中为该 logger 配置单独的 appender
（独立文件、独立保留策略），或直接注册自定义 `AuditEventPublisher`。

需要框架内置的落库能力时追加 `chaos-audit-jdbc-starter`，并显式配置 `chaos.audit.jdbc.enabled=true`
（见 [chaos-audit-jdbc](chaos-audit-jdbc.md)）。

## 内置事件

- `auth.login.success`
- `auth.login.failure`
- `auth.token.revoke`
- `auth.refresh.success`
- `auth.refresh.replay`
- `auth.session.kickout`
- `security.permission.denied`
- `gateway.auth.denied`
- `gateway.blacklist.denied`
- `gateway.tenant.denied`

## 示例

```java
auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS)
        .principalId(loginUser.userId())
        .tenantId(loginUser.tenantId())
        .clientId(registeredClient.getClientId())
        .attributes(AuditSupport.attributes("grantType", "password"))
        .build());
```

多个属性、且大多"有值才写"时用 `AuditAttributes`：

```java
.attributes(AuditAttributes.create()
        .putIfNotBlank("grantType", loginContext.grantType())
        .putIfNotBlank("deviceId", loginContext.deviceId())
        .put("rotated", rotated)
        .build())
```

`putIfNotBlank` 跳过空值（设备号、User-Agent 这类可能缺失的字段），`put` 只跳过 null
（布尔、计数这类"值本身就是结论"的字段不能被当成空值丢掉）。

## 扩展点

业务系统可以提供自己的 Spring Bean 覆盖默认日志发布器：

```java
@Bean
AuditEventPublisher auditEventPublisher(AuditRepository repository) {
    return repository::save;
}
```

推荐扩展方向：

- 写入审计表。
- 投递 Kafka/RocketMQ。
- 对接企业 SIEM 或安全审计平台。
- 按事件动作配置告警规则。

框架已经提供 JDBC 落库适配器，详见 `chaos-audit-jdbc` 和 `chaos-audit-jdbc-starter`。

## 属性脱敏

`AuditAttributeSanitizer` / `DefaultAuditAttributeSanitizer` 位于本模块，所有发布器输出扩展属性前都应调用。

- 默认 `LoggingAuditEventPublisher` 已接入脱敏（旧版本直接打印 attributes，密码、验证码会进入日志平台），
  并把 `reason` 中的换行替换为空格，防止伪造审计日志行。
- 自动装配注册 `AuditAttributeSanitizer` Bean，自定义发布器可以直接注入复用。
- 规则说明见 `chaos-audit-jdbc` 文档的"脱敏策略"一节。

## 注意事项

- 审计事件只记录必要主体、租户、客户端、IP、URI、traceId 和原因，避免写入密码、验证码、完整 token 等敏感值。
- `AuditSupport.event` 读取的主体和租户来自 `RequestContext`，其值只来自认证结果或可信代理，不能被客户端请求头伪造。
- 框架模块只依赖 `AuditEventPublisher` 端口，不直接依赖数据库、MQ 或第三方审计 SDK。
- 新增审计动作时同步更新 `AuditAction`、模块文档和架构测试。

## 异步发布

审计事件默认在请求线程内同步发布。配合 `chaos-audit-jdbc` 时，每次登录、每次权限拒绝都是一次 inline
数据库写入——扫描器批量打未授权接口会把审计写入变成可被外部触发的放大点。

```yaml
chaos:
  audit:
    async:
      enabled: true        # 默认 false；配合 chaos-audit-jdbc 时强烈建议开启
      queue-capacity: 10000
```

开启后请求线程只做一次入队，实际写入在守护线程 `chaos-audit-publisher` 上完成。

- **队列满时丢弃，绝不阻塞**：队列满说明下游已经跟不上，此时阻塞业务只会把下游故障放大成全站故障。
  丢弃计入 `chaos.audit.events{outcome="dropped"}` 并按分钟节流打告警日志。
- 单条事件写入失败不会让工作线程退出，计入 `outcome="failed"`。
- 容器关闭时排空队列，避免优雅停机丢掉最后一批审计。
- **合规要求零丢失时不要开启异步**：改用同步写入，或把审计投递到 MQ。
