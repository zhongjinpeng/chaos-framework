# chaos-boot-parent

## 职责

业务应用推荐使用的 parent POM，作用等同于 `spring-boot-starter-parent` + `chaos-dependencies`：

- **依赖版本**：继承 `chaos-dependencies`，Spring Boot / Spring Cloud / 三方库 / 全部 chaos 模块都不需要写版本。
- **编译**：Java 21、UTF-8、`-parameters`（Spring MVC 参数名解析、构造器绑定依赖它）。
- **资源过滤**：`application*.yml/properties` 支持 `@project.version@` 形式的占位符（与 Spring Boot parent 一致）。
- **测试**：surefire 运行 `*Test`，failsafe 运行 `*IT`（已绑定 `integration-test` / `verify`）。
- **打包**：`spring-boot-maven-plugin` 预置 `repackage`，应用模块声明插件坐标即可生成可执行 jar；
  jar 的 `MANIFEST.MF` 带 `Implementation-Title` / `Implementation-Version`（`addDefaultImplementationEntries`），
  否则 banner 与 Spring Boot 的 `${application.version}` 取不到应用版本。

## 使用方式

```xml
<parent>
    <groupId>com.michael</groupId>
    <artifactId>chaos-boot-parent</artifactId>
    <version>1.0.0</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>com.michael</groupId>
        <artifactId>chaos-web-service-starter</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- 版本与 repackage 执行已由 parent 预置 -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

`spring-boot-maven-plugin` 只放在 `pluginManagement` 中而不是直接启用，原因与 Spring Boot 官方 parent 相同：
多模块项目中的库模块（domain、api 等）继承同一个 parent 时不应被 repackage。

已有公司级 parent 的项目，改为 import BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.michael</groupId>
            <artifactId>chaos-dependencies</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 与框架内部构建的关系

- `chaos-boot-parent` **不继承** `chaos-parent`：框架自身的发布、API 兼容检查（japicmp）、覆盖率、GPG、enforcer 等治理插件不会进入业务应用构建。
- flatten 插件在 `chaos-boot-parent` 与 `chaos-dependencies` 中都声明为 `inherited=false`，只用于把发布出去的 POM 中 `${revision}` 替换为真实版本。
- 以上约束由 `LayerBoundaryArchitectureTest.bootParentShouldOnlyProvideApplicationBuildConventions` 校验。
