# chaos-architecture-tests

## 职责

本模块是仓库治理测试，名称直接表明它不是给业务方使用的测试支持库（后者是 `chaos-test-support`）。

仓库级架构治理测试（test scope，包 `com.michael.chaos.architecture`），在 `./mvnw test` 阶段执行。

| 测试类 | 内容 |
| --- | --- |
| `DependencyDirectionArchitectureTest` | 模块依赖方向规则 1~9（见 [architecture.md 1.1](../architecture.md#11-依赖方向规则)），失败信息标明规则编号、模块、文件与 import |
| `LayerBoundaryArchitectureTest` | 模块边界、自动装配结构、BOM、发布治理与关键安全约定 |
| `ProjectModel` | 共享的 POM 直接依赖解析与源码 import / package 扫描 |

`LayerBoundaryArchitectureTest` 约束：

- 核心契约模块（`chaos-core`、`chaos-domain`、`chaos-audit`、`chaos-tenant`、`chaos-mq`、`chaos-storage`）不得引入 Spring、数据库、Redis、MQ、对象存储 SDK；扫描目录必须真实存在，模块改名后不会被静默跳过。
- starter 无 Java 代码、autoconfigure 使用 `AutoConfiguration.imports`、禁止 `package-info.java`。
- 公共 BOM 与真实模块双向一致；根 POM 不再声明 `dependencyManagement`；模块 POM 不写冗余的 `${project.version}`。
- 承载 `@ConfigurationProperties` 的模块必须引入 configuration processor。
- 自动装配顺序、生产安全检查、审计发布等关键约定。

## 发布与依赖

本模块没有 `src/main`，不提供对外 API：设置了 `maven.deploy.skip=true`，不发布，也不在 `chaos-dependencies` 中登记。
业务方不要依赖 `chaos-architecture-tests`。

## 运行

```bash
./mvnw -B -pl chaos-architecture-tests test
```

运行真实基础设施集成测试：

```bash
./mvnw -B -Pchaos-integration-test verify
```

## 扩展点

- 增加新的核心契约模块时，同步加入 `CONTRACT_SOURCE_ROOTS`。
- 增加任何带 Java 源码的新模块时，在 `DependencyDirectionArchitectureTest.BASE_PACKAGES` 登记基础包，否则规则 9 失败。
- 断言优先解析结构（POM 用 DOM 解析、注解截取整个 `@AutoConfiguration(...)` 片段），避免依赖换行或属性顺序等格式细节。
- 当前规则基于源码/文本扫描，成本低但只能证明“声明存在”；需要验证运行期行为的规则应放到对应模块的 `ApplicationContextRunner` 测试中。
  依赖方向规则刻意没有使用 ArchUnit：架构测试模块不依赖业务模块，单独运行或并行构建时无需等待业务模块编译；
  模块级 optional / test scope 也只能从 POM 准确获得。

## 注意事项

- 普通 `./mvnw test` 不启动 Docker；只有 `chaos-integration-test` profile 会运行 Testcontainers。
- 集成测试类命名为 `*IT`，源码放入对应模块的 `src/integration-test/java`。
