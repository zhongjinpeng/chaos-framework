# chaos-cloud-nacos

> 仓库中不存在 `chaos-nacos` 模块。Nacos 注册/配置约定位于 `chaos-autoconfigure`，
> 通过 `chaos-cloud-nacos-starter` 引入（聚合 Nacos Discovery 与 Nacos Config）；Gateway 动态路由见 [chaos-gateway-nacos](chaos-gateway-nacos.md)。

## 职责

Nacos discovery/config 依赖边界和约定。

## 依赖方式

引入 `chaos-cloud-nacos-starter`。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-cloud-nacos-starter</artifactId>
</dependency>
```


## 配置

`spring.cloud.nacos.discovery.server-addr`，以及 `chaos.nacos.*`（见 [configuration-index](../configuration-index.md)）。

## 生效条件

`ChaosCloudNacosAutoConfiguration`（注册 `NacosConventions`）同时满足以下条件才生效，启动报告中的 `cloud-nacos` 行据此显示：

- 类路径存在 nacos-client（引入了 `chaos-cloud-nacos-starter`）；未引入时显示为“缺少依赖”。
- `spring.cloud.nacos.discovery.enabled` 与 `spring.cloud.nacos.config.enabled` 没有被同时设为 `false`：
  本地开发常用这两个开关脱离 Nacos 运行（如 example-order-service 默认配置），此时命名约定没有意义，不注册。
- `chaos.nacos.enabled` 未关闭（默认开启）。

该自动装配以类路径条件保护，未引入 Nacos 的网关、授权服务器等应用不会被报告为已启用。

## 示例

```java
服务启动后注册到 Nacos。
```

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。

## 注意事项

- 保持模块职责单一，避免把无关能力塞入当前模块。
- 新增公共 API 后同步更新本手册和 architecture.md。
