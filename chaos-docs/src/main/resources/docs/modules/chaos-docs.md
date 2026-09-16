# chaos-docs

## 职责

架构文档、模块手册和使用示例，以 Markdown 形式维护在 `chaos-docs/src/main/resources/docs`。

## 使用方式

直接在仓库中阅读，从 [index.md](../index.md) 开始。

本模块 `packaging` 为 `pom`，设置了 `maven.deploy.skip=true`，**不打包、不发布**，也不在 BOM 中登记；
业务方不需要、也无法以 Maven 依赖的方式引入文档。

## 目录结构

```text
docs/
├── index.md                 文档入口
├── *.md                     架构、配置索引、CI、发布治理、集成测试等横切文档
├── modules/                 与真实 Maven 模块一一对应的手册
├── capabilities/            没有独立模块、跨多个模块实现的能力（分布式锁、幂等、缓存 key、日志）
└── proposals/               尚未在本仓库落地的设计方案（如 chaos-iam）
```

## 维护规则

- `modules/` 下只放真实存在的模块手册，不要再为规划中的模块创建同名手册，避免与 BOM、架构测试产生歧义。

- 新增或删除模块时，同步更新 `index.md`、`architecture.md` 的模块树和职责表、`CLAUDE.md`、`AGENTS.md`。
- 公共配置项变化时同步更新 `configuration-index.md`。
- 构建、CI、发布流程变化时同步更新 `ci-and-smoke.md`、`release-governance.md`。
- 文档不参与 `scripts/verify-structure.sh` 的占位代码扫描，可以正常描述 `TODO`、`System.out` 等规则本身。
