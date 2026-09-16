# 配置模板

按“要搭什么服务”选择场景模板，按“还需要什么能力”追加能力模板。每个模板都经过 `ConfigurationTemplatesTest`
校验（配置项必须真实存在、值必须能绑定到属性类），不会随代码演进而失效。

| 我要搭建 | 引入的 starter | 开发模板 | 生产模板 |
| --- | --- | --- | --- |
| Servlet 业务服务 | `chaos-web-service-starter` | [web-service/application.yml](web-service/application.yml) | [web-service/application-prod.yml](web-service/application-prod.yml) |
| API 网关 | `chaos-gateway-starter` | [gateway/application.yml](gateway/application.yml) | [gateway/application-prod.yml](gateway/application-prod.yml) |
| OAuth2 授权服务器 | `chaos-auth-server-starter` | [auth-server/application.yml](auth-server/application.yml) | [auth-server/application-prod.yml](auth-server/application-prod.yml) |

| 追加能力 | 引入的 starter | 模板 |
| --- | --- | --- |
| 数据库（MyBatis-Plus、多租户、分页） | `chaos-mybatis-starter` | [addons/mybatis.yml](addons/mybatis.yml) |
| Redis（集群限流、幂等、分布式锁、token 黑名单） | `chaos-redis-starter` | [addons/redis.yml](addons/redis.yml) |
| 可靠消息（outbox + Kafka / RocketMQ） | `chaos-mq-starter` | [addons/mq.yml](addons/mq.yml) |
| 对象存储（MinIO / OSS） | `chaos-storage-starter` | [addons/storage.yml](addons/storage.yml) |

## 使用方式

用 archetype 生成项目时（见 [chaos-archetypes](../modules/chaos-archetypes.md)），场景模板已经复制到生成项目中，无需手工操作。

1. 开发模板复制为 `src/main/resources/application.yml`，生产模板复制为 `application-prod.yml`。
2. 生产模板中的 `${...}` 从环境变量、Secret 或配置中心注入，不要把真实值写进仓库。
3. 生产模板顶部注释列出了“生产前置依赖”（通常是 `chaos-redis-starter`）。缺少时，生产安全检查会在启动时直接报出缺什么。

## 设计约定

- **开发模板只写必须改的配置**：`chaos.*` 配置项不超过 10 个，其余全部依赖默认值（由测试约束）。
- **生产模板只写生产必须 / 强烈建议的配置**：每一项都附带“为什么”。
- 三个生产模板配套使用引用 token（授权服务器 `token.type=redis`，网关与业务服务 `token.type=opaque`），注销、踢人实时生效。
  需要 JWT 时，把网关 / 业务服务改为 `jwt`，授权服务器改用 JDBC 授权存储（`client.store-type=jdbc`，自行引入 `spring-boot-starter-jdbc`）。
- 模板中类型化配置（Duration、数字、布尔值）不要写成无默认值的占位符，否则无法通过模板校验。

全部配置项见 [Configuration Reference](../configuration-reference.md)，按领域整理的关键配置与风险提示见 [Configuration Index](../configuration-index.md)。
