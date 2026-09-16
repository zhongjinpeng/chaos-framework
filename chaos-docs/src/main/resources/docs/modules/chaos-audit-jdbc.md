# chaos-audit-jdbc

## 职责

提供审计事件 JDBC 持久化适配器。源码目录为 `chaos-audit/chaos-audit-jdbc`，对外 artifactId 为 `chaos-audit-jdbc`。

该模块只实现基础设施适配，不改变 `chaos-audit` 的核心端口，避免审计核心模块反向依赖数据库。

## 依赖方式

业务应用优先使用 starter：

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-audit-jdbc-starter</artifactId>
</dependency>
```

如果只需要手工装配发布器，可以直接依赖：

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-audit-jdbc</artifactId>
</dependency>
```

## 使用步骤

1. 引入 `chaos-audit-jdbc-starter`。
2. 业务服务提供 `DataSource`，通常由 `spring.datasource.*` 创建。
3. 执行 `classpath:db/chaos-audit-schema.sql` 建表。
4. 配置 `chaos.audit.jdbc.enabled=true`——**引入 starter 不会自动落库**。
5. 按合规要求补充 `sensitive-keywords` 或注册自定义 `AuditAttributeSanitizer`。

```yaml
chaos:
  audit:
    enabled: true
    async:
      # 强烈建议开启：同步模式下每次登录和权限拒绝都是请求线程内的一次数据库写入
      enabled: true
    jdbc:
      enabled: true
      table-name: chaos_audit_event
      fail-fast: false
      mask-value: "******"
      sensitive-keywords:
        - password
        - token
        - secret
        - credential
        - code
        - authorization
        - cookie
```

JDBC 自动装配先于默认日志审计自动装配执行；业务侧声明自己的 `AuditEventPublisher` Bean 后，它会自动让位。

## 表结构

通用建表脚本：

```text
classpath:db/chaos-audit-schema.sql
```

MySQL 建表脚本：

```text
classpath:db/mysql/chaos-audit-schema.sql
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| action | 审计动作编码 |
| outcome | 执行结果 |
| principal_id | 用户或主体 ID |
| tenant_id | 租户 ID |
| client_id | OAuth2 clientId 或调用方应用 |
| trace_id | 链路追踪 ID |
| ip | 客户端 IP |
| uri | 请求 URI |
| reason | 失败或拒绝原因 |
| occurred_at | 事件发生时间 |
| attributes | 已脱敏扩展属性 JSON |

## 独立事务与字段截断

```yaml
chaos:
  audit:
    jdbc:
      enabled: true
      fail-fast: false
      # 默认 true：存在事务管理器时在 REQUIRES_NEW 独立事务中写入
      independent-transaction: true
```

- **独立事务**：旧版本审计写入加入业务事务，业务回滚时 FAILURE/DENIED 审计一起丢失；在 PostgreSQL 上审计 SQL 失败后
  外层事务进入 aborted 状态，即使异常被吞掉，后续业务 SQL 也会全部失败。现在默认使用 `REQUIRES_NEW`。
  注意独立事务会额外占用一个数据库连接，连接池需预留余量。
- **字段截断**：写入前按表结构截断（action 128、outcome 32、principal/tenant/client/trace 128、ip 64、uri/reason 512），
  避免 MySQL 严格模式下超长字段导致写入失败、审计丢失。

## 手工装配示例

```java
@Bean
AuditEventPublisher auditEventPublisher(
        JdbcOperations jdbcOperations,
        ObjectMapper objectMapper,
        AuditAttributeSanitizer sanitizer,
        PlatformTransactionManager transactionManager) {
    TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return new JdbcAuditEventPublisher(
            jdbcOperations,
            objectMapper,
            "chaos_audit_event",
            false,
            sanitizer,
            requiresNew
    );
}
```

## 脱敏策略

脱敏端口已上移到 chaos-audit：`com.michael.chaos.audit.AuditAttributeSanitizer` / `DefaultAuditAttributeSanitizer`，
日志发布器和 JDBC 发布器共用。`com.michael.chaos.audit.jdbc` 包下的同名类型保留为废弃兼容类型。

默认规则（任一命中即脱敏）：

| 规则 | 内容 |
| --- | --- |
| 包含匹配（属性名去掉 `_ - .` 并转小写后包含） | `password`、`passwd`、`token`、`secret`、`credential`、`authorization`、`cookie`、`privatekey`、`apikey`、`smscode`、`verifycode`、`verificationcode`、`captcha`、`authcode` |
| 精确匹配 | `code`、`pin`、`otp`、`cvv`、`pwd` |
| 值特征 | `Bearer xxx`、`Basic xxx`、JWT |

旧版本把 `code` 当作包含匹配，`orderCode`、`errorCode` 等业务字段被误伤；且只看属性名，`detail=Bearer eyJ...` 会原样落库。

`chaos.audit.jdbc.sensitive-keywords` 覆盖的是包含匹配列表，不要把 `code` 这类短词加入其中。
业务系统可以声明自己的 `AuditAttributeSanitizer` Bean，覆盖默认策略。

## 注意事项

- 不要写入密码、验证码、完整 token、cookie、Authorization 头等敏感原文。
- 表名只允许 `table` 或 `schema.table` 格式，且每段只能包含字母、数字和下划线。
- `failFast=false` 时落库失败只记录 warn 日志，不影响业务请求。
- `failFast=true` 适合审计强合规链路，但需要评估数据库可用性对登录和鉴权链路的影响。
