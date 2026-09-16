# chaos-dependencies

## 职责

对外发布的 BOM，同时是整个工程依赖版本的**唯一来源**：

- import Spring Boot、Spring Cloud、Spring Cloud Alibaba、Testcontainers 的 BOM；
- 管理框架用到、但上述 BOM 未管理的三方库版本（MyBatis-Plus、Redisson、springdoc、jjwt、MinIO、OSS、RocketMQ、nimbus-jose-jwt、oauth2-oidc-sdk 等）；
- 管理全部对外发布的 chaos artifact（基础库、autoconfigure、starter）。

`chaos-dependencies` 不继承任何 parent；根 `pom.xml`（`chaos-parent`）反过来以它为 parent。这样业务方 import BOM 时不会带入框架自身的构建插件配置，
框架内部与业务方也不会再各维护一份版本清单。

## 依赖方式

业务父 POM import：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.michael</groupId>
            <artifactId>chaos-dependencies</artifactId>
            <version>${chaos.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

之后引用 starter 时不写版本：

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-web-starter</artifactId>
</dependency>
```

## 维护规则

- 升级 Spring Boot / Spring Cloud / 三方库：只改本模块 `pom.xml` 的 properties。
- 新增对外模块：在本模块登记，否则 `chaos-architecture-tests` 架构测试失败；删除或改名模块时同步删除，否则同样失败（BOM 双向校验）。
- 框架自身模块版本统一使用 `${revision}`，发布时由 `flatten-maven-plugin` 替换。
- `nimbus-jose-jwt` 与 `oauth2-oidc-sdk` 需要成对升级，否则默认构建中的 `dependencyConvergence` 会失败。
- `chaos-architecture-tests`、`chaos-examples`、`chaos-docs` 以及各 `*-parent` 聚合 POM 不进入 BOM。

## 发布元数据

Maven Central 要求的 `url`、`licenses`、`scm`、`developers` 定义在本 POM 并被所有模块继承。
`chaos.project.url`、`chaos.scm.connection` 默认是 `CHANGE-ME` 占位值，正式发布前必须替换，详见 [release-governance.md](../release-governance.md)。
