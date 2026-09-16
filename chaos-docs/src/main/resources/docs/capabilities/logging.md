# 日志约定（chaos-trace）

## 职责

MDC 字段、JSON logback include 和访问日志字段约定。

> 仓库中不存在 `chaos-log` 模块。`MdcKeys`、`MdcSupport`（包 `com.michael.chaos.trace.log`）以及 logback/log4j2 模板都位于 `chaos-observability/chaos-trace`，对外 artifactId 为 `chaos-trace`。

## 依赖方式

通过 `chaos-web-starter` 或 `chaos-gateway-starter` 间接引入（二者都依赖 `chaos-trace`）。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-trace</artifactId>
</dependency>
```


## 配置

默认推荐 Logback。应用的 `src/main/resources/logback-spring.xml` 可直接 include 彩色控制台和按级别按天滚动文件配置：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="com/michael/chaos/trace/log/logback/logback-color-file.xml"/>
</configuration>
```

文件输出目录默认是 `logs/${spring.application.name}`，可通过 `logging.file.path` 调整。按级别输出到 `trace`、`debug`、`info`、`warn`、`error` 子目录，并按天和大小滚动。

```yaml
logging:
  file:
    path: logs
  level:
    root: INFO

chaos:
  logging:
    max-history: 30
    max-file-size: 100MB
    total-size-cap: 10GB
```

如果应用切换到 Log4j2，可使用 `com/michael/chaos/trace/log/log4j2/log4j2-color-file.xml` 作为 `log4j2-spring.xml` 模板。不要在同一个应用里同时启用 Logback 和 Log4j2；切换 Log4j2 时需要排除 `spring-boot-starter-logging` 并引入 `spring-boot-starter-log4j2`。

JSON 日志仍可在 logback 中 include `com/michael/chaos/trace/log/logback/logback-json.xml`。

## 示例

```java
MdcSupport.putIfNotBlank(MdcKeys.USER_ID, userId);
```

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。

## 注意事项

- 保持模块职责单一，避免把无关能力塞入当前模块。
- 新增公共 API 后同步更新本手册和 architecture.md。
