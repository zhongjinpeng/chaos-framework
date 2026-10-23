# chaos-excel 重构方案与技术选型（POI 主引擎）

> 状态：P0/P1 核心已落地（2026-10-23）。当前实现见 [chaos-excel](../modules/chaos-excel.md)。
> 已落地：POI `5.5.1`、`@ExcelColumn` 映射、StAX 导入、SXSSF 导出、多 Sheet 模板、独立示例页、下拉、错误文件、`ImportPlan`/`ImportResult`；已删除 `processBatches`/`readVisibleSheets`/旧模板 DSL/`ImportOperationType`。
> 尚未落地：级联下拉、异步导入、token 下载、模板元数据（P2/P3）。
> 范围：`chaos-data/chaos-excel` 的导入、导出、模板下载、错误文件能力。
> 不包含：业务校验规则、数据库事务编排、导入任务调度。
> 选型前提：按当前偏好采用 **Apache POI 单引擎**，不再把 FastExcel / Fesod 作为主引擎；Fesod 仅作为对比项保留。

## 0. 结论摘要

| 决策点 | 结论 |
| --- | --- |
| Excel 引擎 | Apache POI `5.5.1` 单引擎（已确认）：模板用 `XSSFWorkbook`，导出/错误文件用 `SXSSFWorkbook`，导入用 `XSSFReader` + 自定义 StAX 解析；移除 FastExcel |
| POI 版本 | 先用 `5.5.1`；`6.0.0` 最低 Java 17（项目满足）但 2026-10-22 刚发布，等补丁版本和完整回归后再升级 |
| 行模型映射 | 新增 `@ExcelColumn`，作为表头、列序、宽度、必填、格式、下拉的唯一元数据；已确认接受 `@ExcelProperty` → `@ExcelColumn` 的源码级迁移 |
| 模板实现 | POI 直接写 `XSSFWorkbook`，不再“Fesod 写一遍、POI 再打开增强一遍” |
| 示例数据 | 已确认放独立 `示例-<sheet>` 页，数据页保持干净 |
| 下拉选 | 隐藏字典 Sheet + 工作簿级命名区域；名称做命名空间与哈希去重；级联下拉放到 P2 |
| 多 Sheet 导入 | 显式 `ImportPlan`，每个 Sheet 独立 `rowType`、表头、校验器、处理器 |
| 错误文件 | `ImportResult.errorFile()` 返回流式 `ExcelFile`；已确认采用“成功 JSON / 失败 XLSX”同步返回；token/异步下载暂不纳入框架 |
| 模块形态 | 保持 `chaos-excel` 单模块、Spring-free；P0 不新增 starter |
| 兼容策略 | 保留 `ExcelExports`/`ExcelImports`/`ExcelTemplates` 门面与简单读取 API；删除 `processBatches`/`readVisibleSheets`/旧模板 DSL/`ImportOperationType`；`@ExcelProperty` → `@ExcelColumn` |

### 0.1 已确认决策（2026-10-23）

| 决策 | 结论 |
| --- | --- |
| 行模型注解 | 接受 `@ExcelProperty` → `@ExcelColumn` 的源码级迁移，不做旧注解运行时桥接 |
| POI 版本 | 本次锁定 `5.5.1`，不采用 6.0.0；6.0.x 补丁版本后再评估升级 |
| 错误文件交互 | 同步返回：成功 JSON，失败直接返回错误 XLSX；token 模式不作为 P0 必选 |
| 示例数据 | 放独立 `示例-<sheet>` 页，数据页保持干净，不采用内联示例作为默认 |

## 1. 需求与范围

### 1.1 功能需求

| 编号 | 需求 | 说明 |
| --- | --- | --- |
| R1 | 基本导出 | 单 Sheet、多 Sheet；小文件内存文档，大文件流式写响应 |
| R2 | 基本导入 | 上传暂存、大小/扩展名校验、单 Sheet、多 Sheet、分批处理、行数上限 |
| R3 | 模板下载 | 多 Sheet；示例数据；下拉选；列宽、必填标记、填写提示 |
| R4 | 导入错误文件 | 收集失败行；下载包含原始值与错误原因的 XLSX；支持截断与行号定位 |
| R5 | 资源边界 | 大文件不整本加载；错误集合有界；临时文件可清理 |
| R6 | 安全 | 文件名、压缩炸弹、错误信息、token、临时文件、公式注入边界 |
| R7 | 可测试/可文档化 | 不依赖真实 Excel；POI 断言；模块文档、迁移指南、示例 |

### 1.2 非目标

- 不做业务规则引擎、数据库事务、租户选择、权限校验；这些仍在业务应用层。
- 不做异步导入任务编排、进度查询、断点续传；token 下载由业务层自行实现。
- 不写 `.xls`、CSV、密码保护工作簿、宏、多选下拉（需要 VBA）。
- 不把 `chaos-excel` 变成 Spring 模块；拒绝在核心库中引入 `@Component`、`MessageSource`、`MultipartFile`。

### 1.3 约束

- Java 21、Spring Boot 3.5、Maven 多模块、`chaos-dependencies` 单一版本来源。
- `chaos-excel` 不依赖 Spring MVC / Servlet；HTTP 下载由 `chaos-web` 的 `AttachmentResponses` 组合。
- 只处理 XLSX；读取 `.xls` 作为兼容读取可以评估，但模板和错误文件只输出 XLSX。
- 所有新增公共 API 必须能被现有测试和架构测试覆盖，不能引入 `TODO`、`System.out`。

## 2. 现状评估

### 2.1 已有能力

| 能力 | 当前实现 | 评价 |
| --- | --- | --- |
| 文件抽象 | `ExcelFile`、`ExcelDocument`、`StreamingExcelFile` | 抽象合理，下载层不用感知生成方式，保留 |
| 导出 | `ExcelExports.write(...)`、`writeBatches(...)`、`exportBatches(...)` | 单 Sheet；内存/流式/延迟批次三种形态齐全 |
| 导入 | `ExcelImports.stage/read/readRequired/readBatches`（原 `processBatches`/`processRequired` 已删除） | 暂存、SHA-256、扩展名、字节上限、行数上限已有 |
| 模板 | `ExcelTemplates.write(...)`，隐藏 `_字典` Sheet，命名区域下拉，多 Sheet，示例数据 | 方向正确，但依赖 FastExcel 写、POI 再增强，边界和可用性不足 |
| HTTP 组合 | `chaos-web` 的 `AttachmentResponses.of/streaming` | 与 Excel 模块解耦，保持 |

### 2.2 缺口与风险

| 编号 | 问题 | 影响 | 现状证据 |
| --- | --- | --- | --- |
| G1 | 行号靠计数器推算，不是 Excel 实际行号 | 空行、跳行、多 Sheet 时错误行号偏移，用户无法定位 | `ExcelImports.processBatches(...)` 从 `firstDataRowNumber` 递增 |
| G2 | `readVisibleSheets` 用同一个 `rowType` 读取所有可见 Sheet | 多 Sheet 模板（主表 + 明细）无法按 Sheet 映射不同模型 | `ExcelImports.readVisibleSheets(...)` |
| G3 | 没有表头校验 | 用户改列名/调列顺序后静默错位，导入“成功但数据错” | 读取时只配置 `headRowNumber` |
| G4 | 没有错误收集与错误文件 | 首行失败即抛异常，无法一次性告诉用户全部问题 | `processBatches` 直接抛出 `RowProcessingException` |
| G5 | 示例数据写在数据 Sheet 里 | 用户不删除示例行时，示例会被当作业务数据导入 | `ExcelTemplates.write(...)` 把 `sampleData` 写入数据 Sheet |
| G6 | 模板只给第一行示例设置样式 | 多行示例时后续行无边框、字体、日期格式 | `ExcelTemplates.styleSheet(...)` 只处理 `getRow(1)` |
| G7 | 下拉范围固定 `1..4999`，字典 Sheet 名硬编码 `_字典` | 无法配置范围；业务 Sheet 与 `_字典` 同名时会被污染 | `DEFAULT_LAST_DATA_ROW`、`DICTIONARY_SHEET` |
| G8 | 下拉名称冲突静默首值优先；名称截断可能碰撞 | 同名不同选项时行为不可预期 | `writeDictionary(...)` 的 `putIfAbsent`、`normalizeName(...)` |
| G9 | 缺少必填标记、填写提示、数字/日期/长度校验 | Excel 端提示弱，错误只能等服务端返回 | `Definition` 只有列宽和下拉 |
| G10 | 没有“填写说明”与模板元数据 | 业务方只能口头约定列含义；无法识别模板版本 | 无 |
| G11 | 导出仅单 Sheet | 主表 + 明细导出需要调用方自己拼工作簿 | `ExcelExports` 只接受单个 `rowType` |
| G12 | 没有压缩炸弹、解压后大小、Sheet 数/列数上限 | 恶意 XLSX 可在解析阶段耗尽内存/CPU | `stage` 只限制压缩后的字节数 |
| G13 | 用 `WorkbookFactory.create` 全量加载工作簿来查可见 Sheet | 大文件在“检查 Sheet”阶段就整本进内存，抵消流式读取收益 | `ExcelImports.readVisibleSheets(...)` 内 `WorkbookFactory.create(path.toFile())` |
| G14 | 临时文件只依赖调用方 `close()` | 进程异常退出、业务忘记关闭时残留敏感数据 | `StagedFile` 无 TTL/启动清理 |
| G15 | 引擎与注解绑定 FastExcel，且 FastExcel 1.3.0 已停更 | POI 升级、CVE 修复、新版本兼容没有上游保障 | `cn.idev.excel:fastexcel:1.3.0`，Central 最后更新 2025-08 |
| G16 | `ImportOperationType` 未被任何代码或文档使用 | 公共 API 噪声，容易误导接入方 | 全仓仅枚举自身引用 |
| G17 | 错误消息硬编码中文，没有错误码 | 无法国际化、无法让前端按码处理 | `ExcelProcessingException` 及模板错误文案 |
| G18 | 导出没有多 Sheet、列格式、自动筛选等常用能力 | 复杂报表需要业务方绕过门面直接用 POI | `ExcelExports` |
| G19 | 业务行模型依赖 `@ExcelProperty` | 换引擎或做纯 POI 时，业务模型必须改注解 | `ExcelTemplatesTest.TemplateRow` 等 |

### 2.3 结论

当前实现是一个可用的 POC，导入/导出的主干（暂存、流式、文件抽象）值得保留，但**模板闭环、错误闭环、多 Sheet 映射、资源安全**没有完成。改为 POI 单引擎后，可以去掉 FastExcel 抽象层，把模板、导入、导出统一到一套 POI 能力上；代价是需要自己补一层轻量映射（注解 + 转换器），这也是本次重构的主要工作量。

## 3. 技术选型

### 3.1 Excel 引擎

| 方案 | 读取映射 | 流式写 | 模板控制 | 维护状态 | 结论 |
| --- | --- | --- | --- | --- | --- |
| Apache POI `5.5.1` | 需自建 `@ExcelColumn` + 转换器；底层能力完整 | `SXSSFWorkbook` | 最强，样式/校验/命名区域/隐藏 Sheet 全部可控 | 活跃，5.5.1 稳定 | **采用** |
| FastExcel `1.3.0` | 注解映射、转换器完整 | 支持 | 需 POI 后处理 | 已停更，1.3.0 是末版 | 迁移来源，移除 |
| Apache Fesod `2.1.0-incubating` | 与 FastExcel 同源 | 支持 | 可用 WriteHandler / POI 扩展 | ASF 孵化，2.1.0 已发布 | 备选，不采用 |
| Alibaba EasyExcel | 完整 | 支持 | 完整 | 已停更/归档 | 不采用 |
| `org.dhatim:fastexcel` `0.20.2` | 无读取/映射 | 写侧流式 | 弱 | 活跃 | 不采用，且与 `cn.idev.excel:fastexcel` 重名 |
| JXLS | 无导入 | 模板导出 | 强 | 活跃 | 不采用，能力只覆盖导出 |

**决策（已确认）：Apache POI `5.5.1` 单引擎。**

理由：

1. 模板、导入、导出、错误文件都基于同一套 POI API，不再出现“FastExcel 写一遍、POI 再打开增强一遍”的双引擎路径。
2. 模板需要命名区域、隐藏字典、数据校验、单元格样式、Sheet 保护等能力，POI 是一手支持，控制力最强。
3. 去掉 FastExcel/Fesod 后，依赖面更小，也不再受第三方注解、转换器和发版节奏约束。
4. POI 负责底层读写，`@ExcelColumn` + 轻量转换器负责业务映射；映射层代码量可控，且完全在本仓库内，便于测试和演进。

代价与取舍：

| 代价 | 说明 | 缓解 |
| --- | --- | --- |
| 要自己实现映射层 | 注解解析、字段注入、类型转换、日期/数字格式、枚举、错误定位 | 只做常用类型；用 `CellConverter` 扩展；测试覆盖边界 |
| 要自己实现流式读取 | `XSSFSheetXMLHandler` 只给格式化字符串，拿不到原始类型 | 用 `XSSFReader` + 自定义 StAX 读 `c/v/t/s`，保留原始类型；见 4.5 |
| 大文件导出要自己管临时文件 | `SXSSFWorkbook` 会写临时文件 | `rowAccessWindowSize`、`compressTmpFiles`、`dispose()`、专属临时目录 |
| POI 6.0 刚发布 | 大版本移除废弃 API、DataFormatter 行为变化 | 先锁 5.5.1；6.0 作为独立升级项 |

如果更看重“少写映射代码、快速上线”，Fesod 仍然是合理备选；但按当前偏好，本方案走 POI 单引擎。

### 3.2 版本与依赖策略

当前 `chaos-dependencies`：

```xml
<poi.version>5.4.1</poi.version>
<commons-io.version>2.18.0</commons-io.version>
```

目标：

```xml
<poi.version>5.5.1</poi.version>
<commons-io.version>2.21.0</commons-io.version>
```

要点：

- 只依赖 `org.apache.poi:poi-ooxml`，它会带入 `poi`、`poi-ooxml-lite`、`xmlbeans 5.3.0`、`commons-compress 1.28.0`、`commons-io 2.21.0`、`commons-collections4 4.5.0`、`log4j-api 2.24.3` 等。
- `commons-io` 当前被 `chaos-dependencies` 锁在 `2.18.0`，低于 POI 5.5.1 要求的 `2.21.0`；必须同步上调，否则 `dependencyConvergence` 或运行期行为可能不一致。
- 不需要 `poi-ooxml-full`：`poi-ooxml-lite` 覆盖 XLSX 常用 schema，体积更小；只有需要操作任意 OOXML schema 类时才考虑 full。
- 不引入 `cn.idev.excel:fastexcel`、`org.apache.fesod:fesod-sheet`、`org.dhatim:fastexcel`；依赖树中不应再出现 FastExcel/Fesod。
- POI 6.0.0 的最低 Java 版本是 17（项目 Java 21 满足），但它是 2026-10-22 刚发布的大版本：移除了部分废弃方法，`DataFormatter` 对公式单元格默认使用缓存值。**本次锁 5.5.1（已确认）**，升级门槛：
  1. 6.0.x 有补丁版本；
  2. `SXSSFWorkbook`、`XSSFReader`、`DataFormatter`、日期格式、命名区域/数据校验全部回归；
  3. 依赖收敛和 CVE 扫描通过。
- 许可证为 Apache-2.0，与本仓库一致。

验证命令：

```bash
./mvnw -B -pl chaos-data/chaos-excel -am dependency:tree
./mvnw -B -pl chaos-data/chaos-excel -am test
./mvnw -B -Pchaos-release -Dgpg.skip=true -DskipTests validate
```

### 3.3 模板实现方式

| 方案 | 做法 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. POI `XSSFWorkbook` 直接写 | 表头、示例、样式、下拉、命名区域、隐藏字典全部用 POI | 单引擎、单遍、控制力最强；与导入/导出共用 POI | 模板需要随机访问，不能直接用 SXSSF | **采用** |
| B. `SXSSFWorkbook` 写模板 | 流式写表头/示例，再补校验 | 内存低 | 数据校验/命名区域对已 flush 行不友好；模板本身应很小 | 不采用 |
| C. FastExcel/Fesod 写 + POI 增强 | 当前实现 | 表头映射现成 | 双引擎、二次进出内存、注解绑定 | 移除 |

模板规模上限建议：`maxSheets=10`、`maxSampleRowsPerSheet=1000`、`maxColumns=256`、`maxDropdownOptions=10000`。超过上限直接拒绝，模板生成阶段不应出现大文件。

### 3.4 下拉选实现方式

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 内联列表 `"A,B,C"` | 简单，无隐藏 Sheet | 数据校验内联列表约 255 字符；选项多时不可用 | 只用于极小列表，默认不用 |
| 隐藏字典 + 命名区域 | 选项数量不受 255 字符限制；多 Sheet 复用；可被 `INDIRECT` 级联复用 | 需要维护隐藏 Sheet 和名称 | **默认采用** |
| 级联下拉 `INDIRECT` | 省市区等联动体验好 | 需要父子命名区域约定；模板结构复杂 | P2 |
| 多选下拉 | 用户体验好 | 需要 VBA/宏，`.xlsx` 默认禁用宏 | 不支持，文档注明“逗号分隔 + 服务端拆分” |

实现细节：

- 每个“下拉定义”由 `(name, options)` 唯一确定；相同定义跨 Sheet 复用同一字典列与命名区域。
- 命名区域名不能以数字开头，只允许字母/数字/下划线；统一加前缀 `chaos_dd_`，并对原始名称做 `[^A-Za-z0-9_]` 替换 + 8 位哈希后缀，避免 `a-b` 与 `a_b` 碰撞。
- 同名但选项不同 → 构建模板时直接 `IllegalArgumentException`，不静默首值优先。
- 字典列按定义顺序写入，命名区域公式如 `'_chaos_dict'!$A$1:$A$12`；字典 Sheet 使用保留名 `_chaos_dict`，若用户已有同名 Sheet，追加哈希后缀并在内部记录实际名称。
- 数据校验范围从 `headerRows` 之后开始，到 `lastDataRow`（默认 4999，可配置）；必填列单独建校验并设置 `emptyCellAllowed=false`，错误提示“此项必填”。
- Excel 校验只是输入辅助，**服务端校验才是权威**；文档必须写明。

### 3.5 错误文件交付方式

| 方案 | 做法 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. 同步返回 | 成功返回 JSON；有错误时直接返回错误 XLSX，响应头带统计 | 无存储、无 token、无清理；一次请求闭环 | 同一响应不能既返回完整 JSON 又返回文件；前端需按 `Content-Type` 分流 | **P0 默认（已确认）** |
| B. token + 存储 | 导入返回 token，另开下载接口 | 前端可以先展示统计再下载；适合异步/大文件 | 需要存储、TTL、租户隔离、清理 | 业务侧实现，不进入框架 |
| C. 对象存储 + 预签名 URL | 错误文件写 `chaos-storage`，返回 URL | 多实例天然可用；下载不占应用内存 | 引入对象存储依赖与生命周期管理 | 业务自行实现，不进入框架 |

P0 推荐的交互：

```text
POST /api/import
  ├─ 无错误 → 200 application/json { total, success, failed: 0 }
  └─ 有错误 → 200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
              X-Import-Total / X-Import-Success / X-Import-Failed
              Content-Disposition: attachment; filename="试剂导入错误-20261023-153000.xlsx"
```

前端逻辑：`Content-Type` 是 `application/json` 就展示统计，是 XLSX 就触发下载。这个方案不需要 Redis/OSS，也不会产生“错误文件已过期”的支持成本。

token/异步下载暂不纳入框架：如果业务必须先返回 JSON、再下载错误文件，可以在业务层基于
`ExcelFile` 自行保存到 Redis/OSS，并生成随机 token；框架只负责生成错误文件内容。

### 3.6 多 Sheet 导入映射

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| 同一 `rowType` 读所有可见 Sheet | 原 `readVisibleSheets` | 已删除；多 Sheet 场景统一使用 `ImportPlan` |
| 调用方按 Sheet 名逐个调用 | 灵活但样板代码多，错误聚合困难 | 不采用 |
| `ImportPlan` + 每 Sheet 独立 `SheetImportSpec` | Sheet 名、`rowType`、表头行、校验器、处理器、行数上限都可独立配置 | **采用** |

## 4. 目标设计

### 4.1 模块与包

保持单模块 `chaos-data/chaos-excel`，包根仍为 `com.chaos.excel`。为兼容现有 API，门面类保留在根包，新增类型放子包：

```text
com.chaos.excel
├── ExcelFile.java                 现有文件契约，保留
├── ExcelDocument.java             内存文档，保留
├── StreamingExcelFile.java        流式文件，保留
├── ExcelExports.java              导出门面（新增多 Sheet 重载）
├── ExcelImports.java              导入门面（保留旧重载，新增 execute）
├── ExcelTemplates.java            模板门面（保留旧重载，新增 DSL）
├── ExcelProcessingException.java  统一异常
├── annotation/
│   └── ExcelColumn.java           行模型列注解（替代 @ExcelProperty）
├── mapping/
│   ├── CellValue.java             原始单元格值（类型 + 原始值 + 格式化值）
│   ├── CellConverter.java         单列转换扩展点
│   ├── AutoCellConverter.java     自动转换哨兵
│   ├── ConverterRegistry.java     默认转换器
│   ├── ColumnBinding.java         注解解析后的列绑定
│   └── RowMapper.java             行模型映射
├── export/
│   ├── ExportSheet.java
│   └── ExportOptions.java
├── importer/
│   ├── ImportPlan.java
│   ├── SheetImportSpec.java
│   ├── ImportOptions.java
│   ├── ImportResult.java
│   ├── SheetResult.java
│   ├── ImportError.java
│   ├── ErrorPolicy.java
│   ├── RowHandler.java
│   ├── RowValidator.java
│   └── RowValidationException.java
├── template/
│   ├── TemplateSheet.java
│   ├── ColumnSpec.java
│   ├── TemplateOptions.java
│   └── SamplePlacement.java
├── error/
│   └── ImportErrorReport.java
└── internal/
    ├── FileNames.java              文件名清洗
    ├── HeaderMatcher.java          表头匹配
    └── poi/
        ├── PoiSheetReader.java     XSSFReader + StAX 流式读取
        ├── PoiWorkbookWriter.java  SXSSFWorkbook 流式导出
        ├── PoiTemplateWriter.java  XSSFWorkbook 模板
        └── PoiZipGuard.java        压缩炸弹预检
```

约束：

- `internal` 下的类不出现在公共签名里；公共 API 不返回 `Workbook`/`Sheet`/`Cell`/`Row`。
- 行模型使用本仓库的 `@ExcelColumn`，不再依赖 `@ExcelProperty`；这是唯一有意的注解契约。
- `chaos-excel` 的 `pom.xml` 仍不引入 Spring。

### 4.2 目标 API（示意，非最终签名）

注解与转换器：

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {
    int index() default -1;        // -1 表示按字段声明顺序
    String name();                 // 表头
    boolean required() default false;
    int width() default 0;         // 0 表示自动
    String format() default "";
    String dropdown() default "";  // 模板下拉定义的 key
    String hint() default "";
    Class<? extends CellConverter<?>> converter() default AutoCellConverter.class;
}

public interface CellConverter<T> {
    T read(CellValue cell, ColumnBinding column);
    default Object write(T value, ColumnBinding column) {
        return value;
    }
}
```

`AutoCellConverter` 是内部哨兵实现，表示按字段类型自动选择转换器。

行模型：

```java
public class ReagentRow {
    @ExcelColumn(index = 0, name = "名称", required = true, width = 24,
            hint = "填写试剂中文名称")
    private String name;

    @ExcelColumn(index = 1, name = "单位", required = true, width = 12,
            dropdown = "units")
    private String unit;

    @ExcelColumn(index = 2, name = "有效期", width = 16, format = "yyyy-mm-dd")
    private LocalDate expiry;
}
```

模板：

```java
TemplateSheet reagents = TemplateSheet.builder("试剂", ReagentRow.class)
        .dropdown("units", List.of("g", "mL", "L"))
        .samples(List.of(new ReagentRow("氯化钠", "g", LocalDate.of(2027, 1, 1))))
        .build();

TemplateSheet instruments = TemplateSheet.builder("仪器", InstrumentRow.class)
        .dropdown("instrument_units", List.of("台", "套"))
        .samples(List.of(new InstrumentRow("电子天平", "台")))
        .build();

ExcelDocument template = ExcelTemplates.write(
        "实验室导入模板",
        List.of(reagents, instruments),
        TemplateOptions.defaults()
                .samplePlacement(SamplePlacement.SEPARATE_SHEET)
                .includeInstructions(true)
                .lastDataRow(4999));
```

导入：

```java
ImportPlan plan = ImportPlan.builder()
        .sheet(SheetImportSpec.builder("试剂", ReagentRow.class)
                .headerRows(1)
                .validator(reagentValidator)
                .handler(reagentService::save)
                .maxRows(100_000)
                .build())
        .sheet(SheetImportSpec.builder("仪器", InstrumentRow.class)
                .headerRows(1)
                .validator(instrumentValidator)
                .handler(instrumentService::save)
                .maxRows(50_000)
                .build())
        .errorPolicy(ErrorPolicy.collect(1_000))
        .build();

try (ExcelImports.StagedFile staged = ExcelImports.stage(
        filename, uploadStream, ImportOptions.xlsx(20 * 1024 * 1024))) {
    ImportResult result = ExcelImports.execute(staged, plan);
    if (result.hasErrors()) {
        return result.errorFile()
                .map(file -> AttachmentResponses.streaming(
                        file.filename(), file.contentType(), file::writeTo))
                .orElseThrow();
    }
    return ResponseEntity.ok(result.summary());
}
```

错误文件直接下载（`chaos-web`）：

```java
ExcelFile report = result.errorFile().orElseThrow();
return AttachmentResponses.streaming(
        report.filename(), report.contentType(), report::writeTo);
```

如果需要先返回 JSON、再下载错误文件，业务层可自行保存 `ExcelFile` 并生成 token；
框架不提供存储实现。

导出：

```java
ExcelFile file = ExcelExports.write("实验室数据", List.of(
        ExportSheet.of("试剂", ReagentRow.class, reagentBatches),
        ExportSheet.of("仪器", InstrumentRow.class, instrumentBatches)),
        ExportOptions.defaults()
                .freezeHeader(true)
                .autoFilter(true)
                .formulaSanitize(true));
```

### 4.3 数据模型

```java
public record ImportError(
        String sheetName,
        int rowNumber,          // 1-based，Excel 实际行号
        int columnIndex,        // 0-based，行级错误为 -1
        String columnName,
        String code,            // 稳定错误码，如 EXCEL_REQUIRED / EXCEL_FORMAT
        String message,
        Map<Integer, String> rawCells) {}

public record SheetResult(
        String sheetName,
        long totalRows,
        long successRows,
        long failedRows,
        boolean skipped) {}

public final class ImportResult {
    // 不可变字段：originalFilename、totalRows、successRows、failedRows、
    // errors、errorsTruncated、sheets、sheetHeaders

    public boolean hasErrors();
    public boolean isSuccess();
    public boolean isPartialSuccess();
    public double successRate();
    public int collectedErrorCount();
    public List<ImportError> errorsForSheet(String sheetName);
    public Optional<SheetResult> sheet(String sheetName);
    public Optional<ExcelFile> errorFile(); // 首次生成后缓存
    public ImportSummary summary();
}

public record ImportSummary(
        long totalRows,
        long successRows,
        long failedRows,
        boolean hasErrors,
        List<ImportError> errors) {}

public interface ErrorPolicy {
    static ErrorPolicy failFast();
    static ErrorPolicy collect(int maxErrors);
}
```

说明：

- `errors` 不可变、有界；达到 `maxErrors` 后继续处理有效行，但不再追加错误，`errorsTruncated=true`。
- `ImportResult`/`SheetResult` 在紧凑构造器中复制集合，避免调用方修改内部状态。
- `rawValue` 统一截断到 200 字符，避免错误文件/日志被超长单元格撑爆。
- `message` 面向用户；`code` 面向程序。错误码保持稳定，文案国际化在 P2 通过 `MessageResolver` 接入 Spring `MessageSource`。

### 4.4 模板布局

| Sheet | 可见性 | 内容 | 导入时 |
| --- | --- | --- | --- |
| `填写说明` | 可见 | 模板名称、版本、列说明、必填项、示例位置、注意事项 | 跳过 |
| 数据 Sheet（如 `试剂`） | 可见 | 表头、下拉、必填标记、填写提示；默认不放示例行 | 按 `ImportPlan` 读取 |
| `示例-试剂` | 可见 | 该 Sheet 的示例数据，表头与数据 Sheet 一致 | 跳过 |
| `_chaos_dict` | 隐藏 | 每个下拉定义一列，供命名区域引用 | 跳过 |
| `_chaos_meta` | 隐藏 | 模板 ID、版本、数据 Sheet 列表、生成时间（P2） | 跳过（P2 用于版本校验） |

示例数据放在独立 Sheet（已确认）：如果示例行与数据行混在一起，导入端无法可靠区分“用户没删示例”和“用户真的填了这一行”。独立 `示例-*` Sheet 既能展示用法，又不会污染数据；`SamplePlacement.INLINE` 仅作为后续扩展，不作为默认。

### 4.5 导入流程

```text
1. stage
   - 扩展名白名单
   - 压缩后字节上限
   - SHA-256
   - 临时文件（随机名、600 权限、可 TTL 清理）

2. inspect
   - 用 OPCPackage + XSSFReader 读 workbook.xml 的 Sheet 列表
   - 用 PoiZipGuard 统计条目数、解压后总大小、压缩比
   - 校验 Sheet 数、解压后大小、条目数
   - 解析 ImportPlan：缺 Sheet 直接失败；忽略 填写说明/示例-*/_chaos_dict/_chaos_meta

3. per sheet
   - XSSFReader 打开该 Sheet 的 XML 流
   - 自定义 StAX 读 row/c，保留字符串/数字/布尔/日期/公式缓存/错误类型
   - 首行做表头校验
   - 逐行映射为 CellValue，再交给 RowMapper
   - validator 校验；handler 处理
   - 行级错误按 ErrorPolicy 收集；未知异常立即中止

4. result
   - 聚合 total/success/failed
   - 错误文件按需生成（惰性）
   - 关闭并删除暂存文件
```

为什么不用 `XSSFSheetXMLHandler` 直接读：

- 它的 `SheetContentsHandler` 只回调 `cell(String cellReference, String formattedValue, XSSFComment)`，拿到的是格式化后的字符串，**丢失原始单元格类型**（数字/日期/布尔/公式/错误），后续按目标类型反解析容易出现“日期变字符串、1,234 变 1”的问题。
- 自定义 StAX 直接读 `sheetN.xml` 的 `<c t="s|n|b|e|str|inlineStr">`、`<v>`、`<is>`，配合 `SharedStringsTable`、`StylesTable`、`DateUtil.isADateFormat`，能拿到可靠类型和原始值。
- 如果工期紧，可以先用 `XSSFSheetXMLHandler` 做 v1，但需要在文档中明确“只保证格式化字符串语义”；本方案按长期可维护性选择自定义 StAX。

关键规则：

- **行号**：XML 的 `r` 属性或按顺序计数，统一转成 1-based 实际行号；批量回调同时携带 `int[] rowNumbers`，与 `List<T>` 对齐。
- **表头**：期望表头来自 `@ExcelColumn.name`（优先）或 `ColumnSpec`；`HeaderPolicy.STRICT` 下缺列/多列/顺序不符直接失败；`LENIENT` 只记录警告。默认 STRICT，模板导入场景更安全。
- **公式**：只读 `<v>` 缓存结果，**不调用 `FormulaEvaluator`**；没有缓存值时报 `EXCEL_FORMULA_NO_CACHE`。这既是正确性问题，也是安全边界。
- **日期/数字**：数字单元格按目标类型转换；日期通过样式索引 + `DateUtil.isADateFormat` 判定，再按 `LocalDate`/`LocalDateTime` 转换；字符串按 `@ExcelColumn.format` 或 ISO 解析。
- **共享字符串/内联字符串**：`t="s"` 走 `SharedStringsTable`，`t="inlineStr"` 读 `<is><t>`；两者都要支持。
- **合并单元格**：P0 只读取左上角，其他区域按空处理；文档注明，不做自动填充。
- **事务**：框架不开启事务。`handler` 按批/按行自行决定事务边界；`ErrorPolicy.collect` 不保证已处理行回滚。文档必须写明“部分成功”的语义。
- **批大小**：默认 500，可配；`ImportPlan` 的 `SheetImportSpec` 可覆盖。
- **未知异常**：转换错误、校验错误、`RowValidationException` 可收集；数据库异常、`Error`、业务非行级异常立即抛出，避免“吞掉系统故障”。

### 4.6 错误文件格式

每个失败 Sheet 生成一个错误 Sheet，命名为 `错误-<原名>`（超过 31 字符截断并加序号），列布局：

| 行号 | 工作表 | 原始列 1 | 原始列 2 | … | 错误字段 | 错误原因 | 错误码 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 3 | 试剂 | 氯化钠 | g | … | 单位 | 单位必须是 g/mL/L | EXCEL_ENUM |

规则：

- 只包含失败行；原始列保持用户上传时的表头与值，便于对照。
- `行号` 是 Excel 实际行号；`工作表` 在多 Sheet 时必填，单 Sheet 也保留，便于统一解析。
- 原始值按字符串写入；日期用原单元格显示格式，避免时区二次转换。
- 错误原因列红色字体；表头红色底、冻结首行；列宽按内容估算并设上限。
- 错误文件不写公式；所有内容按字符串/数字写入。
- 达到 `maxErrors` 时，在错误 Sheet 末尾追加一条汇总说明，写明“仅保留前 N 条错误”；`sheetResult.failedRows` 仍是真实失败数。
- 文件名：`<模板名>-导入错误-yyyyMMddHHmmss.xlsx`，经内部 `FileNames` 清洗；下载用 UTF-8 `Content-Disposition`。
- 使用 `SXSSFWorkbook` 流式写错误文件，行数受 `maxErrors` 限制，不会把整个导入结果留在内存。

### 4.7 导出

目标能力：

```java
ExcelFile file = ExcelExports.write("实验室数据", List.of(
        ExportSheet.of("试剂", ReagentRow.class, reagentBatches),
        ExportSheet.of("仪器", InstrumentRow.class, instrumentBatches)),
        ExportOptions.defaults()
                .freezeHeader(true)
                .autoFilter(true)
                .formulaSanitize(true));
```

- 多 Sheet：一个 `SXSSFWorkbook`，按顺序创建 Sheet；每个 Sheet 独立 `rowType`、表头、批次。
- 流式：`new SXSSFWorkbook(rowAccessWindowSize)`，默认窗口 100 行；超过窗口的行刷到临时文件，不常驻内存。
- 临时文件：`SXSSFWorkbook` 使用 `java.io.tmpdir`；可设置 `compressTmpFiles(true)`，写完后调用 `dispose()` 删除临时文件，**不关闭调用方输出流**。
- 列宽/格式：从 `@ExcelColumn` 的 `width`/`format` 生成 `CellStyle`；导出前按前 N 行采样估算宽度，避免全量扫描。
- 表头/样式：加粗、居中、背景色、边框、冻结首行、自动筛选；样式复用 `CellStyle`，避免每个单元格新建样式。
- 公式注入：只写字符串/数字/布尔/日期单元格，不写公式；为未来 CSV/文本导出预留 `formulaSanitize`，对 `= + - @ \t \r` 开头的文本加 `'` 前缀。默认开启，允许显式关闭。
- 文件抽象：小文件返回 `ExcelDocument`，大文件返回 `StreamingExcelFile`；`ExcelFile.writeTo` 可重复调用（流式实现每次重新拉批次）。

### 4.8 扩展点与配置

P0 保持零配置、无 Spring：

- 所有限制通过 `ImportOptions` / `ReadOptions` / `TemplateOptions` / `ExportOptions` 传入。
- 默认值集中在 `ExcelDefaults`（public final class，仅常量），避免散落魔法数。

默认限制建议：

| 项 | 默认值 |
| --- | --- |
| 上传压缩后大小 | 20 MB |
| 解压后总大小 | 200 MB |
| 单文件 Sheet 数 | 20 |
| 单 Sheet 数据行 | 100,000 |
| 单 Sheet 列数 | 256 |
| 单元格字符数 | 32,767（Excel 硬限制，超出拒绝） |
| 批大小 | 500 |
| 收集错误数 | 1,000 |
| 模板 Sheet 数 | 10 |
| 模板每 Sheet 示例行 | 1,000 |
| 下拉选项数/组 | 10,000 |
| 错误文件行数 | 同 maxErrors |
| SXSSF 窗口 | 100 行 |

如果 P1 引入指标、`MessageSource`，再按仓库规范增加：

- `chaos-autoconfigure` 的 `com.chaos.autoconfigure.excel`：只做装配，依赖 optional。
- `chaos-excel-starter`：纯依赖聚合，无 Java 代码。
- 配置前缀 `chaos.excel.*`，字段 Javadoc 生成元数据，并更新 `configuration-reference.md`。

在 P0 之前不创建 starter：`chaos-excel` 没有需要 Spring 管理的 bean，强行加 starter 只会增加维护面。

### 4.9 兼容性与迁移

| 现有 API | 处理 |
| --- | --- |
| `ExcelExports.write/writeBatches/exportBatches` | 保留；新增多 Sheet 重载 |
| `ExcelImports.stage/ReadOptions/StagedFile` | 保留；新增 `ImportOptions` 与 `execute` |
| `ExcelImports.processBatches(...)` 带 `firstDataRowNumber` | 已删除；改用 `ImportPlan` / `ImportResult` |
| `ExcelImports.readVisibleSheets(...)` | 已删除；改用 `ImportPlan` |
| `ExcelTemplates.Sheet/Definition/Dropdown` | 已删除；改用 `TemplateSheet` / `ColumnSpec` |
| `ImportOperationType` | 已删除 |
| 行模型 `@ExcelProperty` | 已确认改为 `@ExcelColumn`；这是本次主要源码级变更 |

`@ExcelProperty` → `@ExcelColumn` 映射：

| FastExcel 注解 | 新注解 |
| --- | --- |
| `@ExcelProperty(value = "名称", index = 0)` | `@ExcelColumn(name = "名称", index = 0)` |
| `@ExcelProperty(converter = Xxx.class)` | `@ExcelColumn(converter = Xxx.class)`（新 `CellConverter` 接口） |
| `@DateTimeFormat("yyyy-MM-dd")` | `@ExcelColumn(format = "yyyy-MM-dd")` |
| `@NumberFormat("#,##0.00")` | `@ExcelColumn(format = "#,##0.00")` |
| `@ColumnWidth(20)` | `@ExcelColumn(width = 20)` |
| `@ExcelIgnore` | 不加 `@ExcelColumn` 即可 |

迁移步骤：

1. 删除 `cn.idev.excel:fastexcel` 依赖，加入 `org.apache.poi:poi-ooxml`。
2. 把行模型上的 `@ExcelProperty` 换成 `@ExcelColumn`；列名、列序、格式保持不变。
3. 自定义转换器改为实现 `CellConverter`；日期/数字优先用内置转换器。
4. 多 Sheet、错误文件改用 `ImportPlan`/`ImportResult`；旧的单 Sheet API 仍可编译。
5. 模板下载改用 `TemplateSheet`/`ColumnSpec`。

说明：`@ExcelProperty` 是 FastExcel 的注解，去掉 FastExcel 后无法在运行期兼容读取；本次不做“反射识别旧注解”的隐式桥接，避免把已移除的引擎重新拖进依赖树。业务侧用 codemod/正则替换即可。

## 5. 安全与资源边界

| 威胁 | 控制 |
| --- | --- |
| 压缩炸弹 | stage 阶段限制压缩字节；`PoiZipGuard` 用 ZIP 条目统计解压后总大小、条目数、压缩比；超限拒绝后再交给 `OPCPackage` |
| 超大 Sheet/行/列 | `maxSheets`、`maxRows`、`maxColumns`、单元格字符数；解析前和解析中双重限制 |
| 路径穿越/响应头注入 | 文件名只取 basename，去除控制字符，长度上限；`Content-Disposition` 用 UTF-8 编码 |
| 临时文件泄露 | 随机文件名、POSIX 600、`close()` 删除、启动/定时清理过期文件；SXSSF 临时文件在 `dispose()` 中删除 |
| 公式注入/公式执行 | 只读公式缓存值，不调用 `FormulaEvaluator`；导出不写公式；为 CSV/未来格式保留 `formulaSanitize` |
| XML 外部实体（XXE） | StAX 工厂关闭 DTD 和外部实体；只接受 XLSX；不解析用户提供的 XML 外部资源 |
| 错误信息泄露 PII | `rawValue` 截断；日志只记录行号/错误码，不记录完整单元格 |
| token 猜测/越权 | 128 位随机 token；绑定租户/用户；TTL；可选一次性 |
| 依赖 CVE | BOM 单点版本；enforcer `bannedDependencies`/`dependencyConvergence`；发布前 dependency-review |
| 线程安全 | 门面全部无状态；`StagedFile` 的打开/关闭保持同步；不引入静态可变配置 |
| POI 全局安全设置 | `ZipSecureFile` 是 JVM 全局配置，不能只靠它做单文件防护；必须配合 `PoiZipGuard` 的按文件预检 |

## 6. 测试与验收

### 6.1 测试矩阵

| 区域 | 用例 |
| --- | --- |
| 模板 | 多 Sheet；示例独立页；下拉命名区域；必填校验；列宽；字典隐藏；同名不同选项拒绝；`_chaos_dict` 冲突；Sheet 名非法/超长；样式复用 |
| 映射 | `@ExcelColumn` 列序/列名；String/数字/布尔/日期/枚举/BigDecimal；空单元格；原始类型；自定义 `CellConverter`；错误码 |
| 导入 | 单/多 Sheet 不同 `rowType`；真实行号（含空行）；表头缺失/多余/顺序错；共享字符串/内联字符串；公式缓存；合并单元格；错误截断；空文件；隐藏/说明/示例 Sheet 跳过 |
| 错误文件 | 仅失败行；原始值；错误字段/原因/码；行号；多 Sheet 分页；文件名；流式写出；无错误时 `Optional.empty()` |
| 导出 | 单/多 Sheet；批次；不关闭调用方流；列宽/格式；空数据；冻结/筛选；公式转义开关；SXSSF 临时文件清理 |
| 资源 | 20 MB 上限；解压后上限；Sheet/行/列上限；临时文件清理；并发 stage/close；SXSSF 窗口 |
| 安全 | 压缩炸弹；文件名穿越；响应头注入；超长单元格；PII 截断；公式缓存缺失 |
| 性能 | 50k 行导入/导出 smoke；错误文件 1k 行；内存不随总行数线性增长（以批次断言为主） |
| 架构 | `chaos-excel` 无 Spring import；包名在 `BASE_PACKAGES`；`verify-structure.sh` 通过 |

### 6.2 验收标准

| 需求 | 验收标准 |
| --- | --- |
| R1 基本导出 | 单/多 Sheet 可下载；50k 行流式导出不 OOM；不关闭调用方流；文件名/MIME 正确 |
| R2 基本导入 | 暂存校验、单/多 Sheet、分批、行数上限、空数据均有测试；错误行号与 Excel 实际行一致 |
| R3 模板 | 至少 2 个数据 Sheet + 2 个示例 Sheet；下拉在 POI 断言中可见且范围正确；必填标记/提示/列宽生效；示例不会被导入 |
| R4 错误文件 | 只含失败行；含原始列 + 错误字段/原因/码；多 Sheet 分页；截断标记；可直接用 `AttachmentResponses` 下载 |
| R5 资源边界 | 压缩炸弹、超大文件、超多 Sheet/行/列均被拒绝；临时文件在正常/异常路径都清理 |
| R6 安全 | 文件名、token、PII、公式缓存、XXE、依赖检查有对应测试或门禁 |
| R7 文档/兼容 | 模块文档更新；`@ExcelColumn` 迁移指南；CHANGELOG；现有 API 测试通过；`verify-structure.sh` 通过 |

### 6.3 验证命令

```bash
./mvnw -B -pl chaos-data/chaos-excel -am test
./mvnw -B -pl chaos-data/chaos-excel -am verify
scripts/verify-structure.sh
python3 scripts/generate-configuration-reference.py --check   # 仅当新增 @ConfigurationProperties
```

## 7. 实施计划

| 阶段 | 工作量 | 内容 | 退出标准 |
| --- | --- | --- | --- |
| P0 依赖与基线（已落地） | 0.5–1 人日 | 移除 FastExcel，加入 POI 5.5.1；`commons-io` 对齐 2.21.0；dependency tree/convergence；现有测试先绿 | 现有测试全绿；依赖树无 FastExcel/Fesod；无版本冲突 |
| P1 核心闭环（已落地） | 5–7 人日 | `@ExcelColumn` + 映射层；`PoiSheetReader`（StAX）；`PoiWorkbookWriter`（SXSSF）；模板 DSL（多 Sheet、示例页、下拉、必填、列宽、说明页）；`ImportPlan`（多 Sheet、表头校验、真实行号、错误策略）；`ImportResult` + 同步错误文件；多 Sheet 导出；安全边界 | R1–R4 验收通过；单测覆盖模板/映射/导入/错误文件/导出 |
| P2 增强 | 2–3 人日 | 数字/日期/长度校验；`_chaos_meta` 模板版本；示例工程控制器；i18n resolver；token 下载方案（业务侧） | R5–R7 验收通过；文档/示例完整 |
| P3 可选 | 3–5 人日 | 级联下拉；异步导入任务；CSV/`.xls` 读取；指标与审计；`chaos-excel-starter` | 按业务需求排期，不阻塞 P0/P1 |

工作量说明：比“Fesod 单引擎”方案多约 2–3 人日，主要花在 `@ExcelColumn` 映射层和 StAX 流式读取上；这是选择 POI 单引擎的主要成本。

任务拆解（P1）：

1. 依赖迁移：移除 `fastexcel`，加入 `poi-ooxml` 5.5.1，`commons-io` 对齐 2.21.0。
2. 映射层：`@ExcelColumn`、`ColumnBinding`、`RowMapper`、`CellValue`、`ConverterRegistry`；默认转换器覆盖常用类型。
3. 流式读取：`PoiSheetReader` 用 `XSSFReader` + StAX 读 workbook.xml 和 sheet XML；共享字符串/内联字符串/样式/日期/公式缓存。
4. 模板：`PoiTemplateWriter` 用 `XSSFWorkbook` 写表头、示例页、说明页、隐藏字典、命名区域、数据校验、样式。
5. 导入：`ImportPlan`/`SheetImportSpec`；表头校验、真实行号、批量回调、错误收集。
6. 错误文件：`ImportErrorReport` 用 `SXSSFWorkbook` 流式生成；截断/列宽/样式。
7. 导出：`PoiWorkbookWriter` 多 Sheet、批次、列宽/格式、冻结/筛选、临时文件清理。
8. 安全与资源：`PoiZipGuard`、上限、临时文件清理、文件名清洗。
9. 测试与文档：单测、POI 断言、迁移指南、模块文档、CHANGELOG。

## 8. 风险与剩余决策

| 风险/决策 | 影响 | 建议 |
| --- | --- | --- |
| POI 单引擎需要自建映射层 | 开发量增加 2–3 人日；边界类型多 | 只做常用类型 + `CellConverter` 扩展；用测试锁定字符串/数字/日期/公式/空值 |
| POI 6.0.0 刚发布 | 大版本行为变化、废弃 API 移除 | 本次锁 5.5.1；6.0.x 补丁版本后再升级 |
| `commons-io` 版本冲突 | POI 5.5.1 要求 2.21.0，仓库当前 2.18.0 | 同步上调并跑 `dependencyConvergence` |
| StAX 读取实现复杂 | 共享字符串、内联字符串、样式、公式缓存、合并单元格 | 先覆盖 XLSX 常用子集；合并单元格 P0 只读左上角；测试用 POI 生成样本 |
| 模板用 XSSFWorkbook | 模板大时内存高 | 模板规模硬上限；模板不承载大示例数据 |
| 示例数据放独立页 | 与部分用户“示例就在数据页”的预期不同 | 默认独立页；保留 `INLINE` 选项并强制 `skipSampleRows` |
| 同步错误文件 vs token | 前端交互与后端存储成本 | 默认同步；token 下载由业务自行保存 `ExcelFile` 实现 |
| Excel 数据校验不是安全边界 | 用户可绕过下拉/必填 | 服务端 `validator` 必须重复校验；文档强调 |
| 注解迁移 | 业务模型必须从 `@ExcelProperty` 改为 `@ExcelColumn` | 提供映射表和 codemod；在 CHANGELOG/迁移指南中显著标注 |
| 错误消息 i18n | 当前硬编码中文 | 错误码已稳定；P2 接 `MessageResolver` 与 `MessageSource` |
| 临时文件清理 | 进程崩溃残留 | `close()` + 启动清理 + 定时清理；SXSSF `dispose()`；部署侧临时目录权限 |

已确认项见 §0.1，不再作为待定项。

剩余待确认：

1. 是否需要在本期就提供 `chaos-excel-starter` 与 `chaos.excel.*` 配置？当前建议不需要。
2. 默认上限（20 MB / 100k 行 / 1k 错误 / SXSSF 100 行窗口）是否符合主要业务场景？可按业务调整。

## 9. 参考

- 当前模块文档：[chaos-excel](../modules/chaos-excel.md)
- Apache POI 变更日志（含 6.0.0 的 JDK 17、DataFormatter 行为说明）：<https://poi.apache.org/changes.html>
- Apache POI 下载与版本：<https://poi.apache.org/download.html>
- POI Javadocs：<https://poi.apache.org/apidocs/>
- Maven Central：<https://repo1.maven.org/maven2/org/apache/poi/poi-ooxml/5.5.1/>
- 现有 HTTP 下载工具：`chaos-web` 的 `AttachmentResponses`
