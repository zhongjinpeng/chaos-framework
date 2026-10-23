# chaos-excel

> 重构方案与选型依据见 [chaos-excel 重构方案](../proposals/chaos-excel-refactor.md)。
> 本文描述当前已实现的行为。

## 职责

`chaos-excel` 基于 Apache POI 提供统一的 XLSX 导入、导出、模板下载和错误文件能力，不依赖 Spring MVC 或 Servlet。
Controller 负责上传、下载协议，业务服务负责数据校验与持久化，本模块只负责表格读写和资源边界。

- 模板、导出、错误文件：`XSSFWorkbook` / `SXSSFWorkbook`
- 导入：`XSSFReader` + StAX 读取 `sheetN.xml`，保留原始单元格类型
- 行模型：`@ExcelColumn`

## 依赖方式

```xml
<dependency>
    <groupId>com.chaos</groupId>
    <artifactId>chaos-excel</artifactId>
</dependency>
```

## 行模型

```java
import com.chaos.excel.annotation.ExcelColumn;
import java.time.LocalDate;

public class ReagentRow {

    @ExcelColumn(name = "名称", index = 0, required = true, width = 24)
    private String name;

    @ExcelColumn(name = "单位", index = 1, required = true, width = 12,
            dropdown = "units", hint = "从下拉列表选择")
    private String unit;

    @ExcelColumn(name = "有效期", index = 2, format = "yyyy-MM-dd", width = 16)
    private LocalDate expiry;

    public ReagentRow() {
    }

    public ReagentRow(String name, String unit, LocalDate expiry) {
        this.name = name;
        this.unit = unit;
        this.expiry = expiry;
    }

    // getter / setter 省略
}
```

`@ExcelColumn` 同时描述表头、列序、必填、列宽、显示格式、下拉 key 和填写提示。
行模型必须有无参构造器；字段通过反射写入。支持 `String`、数字、布尔、`LocalDate`、
`LocalDateTime`、`Date`、枚举、`BigDecimal` 和自定义 `CellConverter`。

## 模板下载示例

Service 层声明模板结构：

```java

import com.chaos.excel.template.SamplePlacement;
import com.chaos.excel.template.TemplateOptions;
import com.chaos.excel.template.TemplateSheet;

import java.time.LocalDate;
import java.util.List;

public ExcelDocument reagentTemplate() {
  TemplateSheet reagents = TemplateSheet.builder("试剂", ReagentRow.class)
          .dropdown("units", List.of("g", "mL", "L"))
          .samples(List.of(
                  new ReagentRow("氯化钠", "g", LocalDate.of(2027, 1, 1)),
                  new ReagentRow("无水乙醇", "mL", LocalDate.of(2027, 6, 1))))
          .instructions("按列填写；单位从下拉列表选择")
          .build();

  TemplateSheet instruments = TemplateSheet.builder("仪器", InstrumentRow.class)
          .dropdown("instrument_units", List.of("台", "套"))
          .samples(List.of(new InstrumentRow("电子天平", "台")))
          .build();

  return ExcelTemplates.write(
          "实验室导入模板",
          List.of(reagents, instruments),
          TemplateOptions.defaults()
                  .samplePlacement(SamplePlacement.SEPARATE_SHEET)
                  .includeInstructions(true)
                  .lastDataRow(4999));
}
```

Controller 直接返回附件：

```java
@GetMapping("/excel/template")
public ResponseEntity<byte[]> template() {
    ExcelDocument template = reagentTemplate();
    return AttachmentResponses.of(
            template.filename(), template.contentType(), template.content());
}
```

`List.of(...)` 中的每个 `TemplateSheet` 都会生成独立的数据 Sheet，各自拥有行模型、列定义、
示例数据、下拉和填写提示。上面的模板会生成：

- 数据 Sheet：`试剂`、`仪器`
- `示例-试剂`、`示例-仪器`：各自的示例数据，导入时不会被读取
- `_chaos_dict`：隐藏字典，多个 Sheet 的下拉共享命名区域
- `填写说明`：可选的填写说明页

如果必须把示例数据放在数据页，可使用 `SamplePlacement.INLINE`，导入时通过
`SheetImportSpec.skipSampleRows(n)` 显式跳过。

## 导入示例

### Service：声明导入计划

```java

import com.chaos.excel.importer.ErrorPolicy;
import com.chaos.excel.importer.ImportPlan;
import com.chaos.excel.importer.ImportResult;
import com.chaos.excel.importer.RowValidationException;
import com.chaos.excel.importer.SheetImportSpec;

public ImportResult importReagents(ExcelImports.StagedFile staged) {
    ImportPlan plan = ImportPlan.builder()
            .sheet(SheetImportSpec.builder("试剂", ReagentRow.class)
                    .headerRows(1)
                    .validator((row, rowNumber) -> {
                        if (row.getName() == null || row.getName().isBlank()) {
                            throw new RowValidationException(
                                    "REAGENT_NAME_REQUIRED", "名称", "名称不能为空");
                        }
                    })
                    .handler((row, rowNumber) -> reagentService.save(row))
                    .maxRows(100_000)
                    .build())
            .errorPolicy(ErrorPolicy.collect(1_000))
            .build();
    return ExcelImports.execute(staged, plan);
}
```

`ImportPlan` 支持每个 Sheet 独立配置 `rowType`、表头行数、校验器、处理器、行数上限、
跳过示例行数和是否允许空数据。`execute` 会校验表头、使用真实 Excel 行号、收集行级错误。

### Controller：成功 JSON / 失败错误文件

```java
@PostMapping("/excel/import")
public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile upload)
        throws IOException {
    try (ExcelImports.StagedFile staged = ExcelImports.stage(
            upload.getOriginalFilename(),
            upload.getInputStream(),
            ExcelImports.Options.xlsx(20 * 1024 * 1024))) {

        ImportResult result = reagentService.importReagents(staged);
        if (result.hasErrors()) {
            ExcelFile report = result.errorFile().orElseThrow();
            return AttachmentResponses.streaming(
                    report.filename(), report.contentType(), report::writeTo);
        }
        return ResponseEntity.ok(result.summary());
    }
}
```

错误文件只包含失败行，列布局为：

```text
行号 | 工作表 | 原始列 1 | 原始列 2 | ... | 错误字段 | 错误原因 | 错误码
```

行号是 Excel 实际行号；错误码是稳定值，前端可以按码处理。默认同步返回“成功 JSON / 失败 XLSX”，
不需要 Redis 或对象存储。如果业务必须先返回 JSON、再下载错误文件，可以在业务层基于
`ExcelFile` 自行保存并生成 token；框架只负责生成错误文件内容。

`ImportResult` 还提供以下派生信息，便于接口层直接使用：

```java
result.isSuccess();             // 是否全部成功
result.isPartialSuccess();      // 是否有成功也有失败
result.successRate();           // 成功率，0.0 ~ 1.0
result.collectedErrorCount();   // 实际保留的错误条数（可能小于 failedRows）
result.errorsForSheet("试剂");   // 指定 Sheet 的错误
result.sheet("试剂");            // 指定 Sheet 的统计
```

`errorFile()` 首次调用时生成错误文件并缓存，重复调用返回同一个 `ExcelFile`，适合在
“先判断是否有错误、再写响应”的场景中避免重复构建工作簿。

### 错误策略

```java
// 第一条错误立即中止
.errorPolicy(ErrorPolicy.failFast())

// 收集错误并继续处理，最多保留 1000 条
.errorPolicy(ErrorPolicy.collect(1_000))
```

`ErrorPolicy.collect` 不保证已处理行回滚；`handler` 应按批或按行自行决定事务边界。
如果业务要求“全部成功或全部失败”，应使用 `failFast` 并在应用层包事务。

## 导出示例

### 多 Sheet 内存导出

```java

import com.chaos.excel.export.ExportOptions;
import com.chaos.excel.export.ExportSheet;

import java.util.List;

ExcelDocument document = ExcelExports.write("实验室数据", List.of(
                ExportSheet.of("试剂", ReagentRow.class, reagentBatches),
                ExportSheet.of("仪器", InstrumentRow.class, instrumentBatches)),
        ExportOptions.defaults()
                .freezeHeader(true)
                .autoFilter(true)
                .formulaSanitize(true));
```

### 大文件流式下载

```java
ExcelFile file = ExcelExports.exportBatches(
        "监测记录",
        SurveyExportRow.class,
        () -> repository.findExportBatches(1_000),
        new ExcelExports.WriteOptions("监测记录", true));

return AttachmentResponses.streaming(
        file.filename(), file.contentType(), file::writeTo);
```

导出使用 `SXSSFWorkbook`，默认窗口 100 行，写完会清理临时文件；框架不会关闭调用方传入的流。
`formulaSanitize` 默认开启，会为以 `= + - @` 开头的文本加 `'` 前缀。

## 简单读取 API

只需要读取第一个 Sheet、且不需要错误文件时，可以使用保留的轻量 API：

```java
try (ExcelImports.StagedFile staged = ExcelImports.stage(
        filename, uploadStream, ExcelImports.Options.xlsx(20 * 1024 * 1024))) {
    List<ReagentRow> rows = ExcelImports.read(staged, ReagentRow.class);
}
```

分批读取：

```java
ExcelImports.readBatches(
        staged,
        ReagentRow.class,
        new ExcelImports.ReadOptions(0, 1, 500, 100_000),
        batch -> reagentService.saveBatch(batch));
```

这两个方法不做表头校验，也不聚合错误；多 Sheet、模板导入或需要错误文件时请使用
`ImportPlan` / `ImportResult`。

## 模板结构

| Sheet | 可见性 | 内容 | 导入时 |
| --- | --- | --- | --- |
| `填写说明` | 可见 | 模板名称、列说明、必填项、示例位置 | 跳过 |
| 数据 Sheet（如 `试剂`） | 可见 | 表头、下拉、必填标记、填写提示 | 按 `ImportPlan` 读取 |
| `示例-<sheet>` | 可见 | 示例数据 | 跳过 |
| `_chaos_dict` | 隐藏 | 下拉字典，命名区域引用 | 跳过 |

下拉项使用隐藏字典和名称管理器承载，不受 Excel 直接列表 255 字符限制；同一个定义可以被多个
Sheet 复用，名称带哈希后缀，避免 `a-b` 与 `a_b` 碰撞。

## 边界

- 只处理 XLSX；不写 `.xls`、CSV、宏或密码保护工作簿。
- 不开启数据库事务；`handler` 按批/按行自行决定事务边界，`ErrorPolicy.collect` 不保证已处理行回滚。
- 上传文件字节上限由 `ExcelImports.stage` 执行；行数、列数、Sheet 数、解压后大小、压缩比由
  `ImportOptions` / `ImportPlan` 执行。
- Excel 下拉和必填只是输入辅助，**服务端校验才是权威**。
- 公式单元格只读取缓存值，不调用 `FormulaEvaluator`；没有缓存值时报 `EXCEL_FORMULA_NO_CACHE`。
- 合并单元格 P0 只读取左上角，其他区域按空处理。
- `@ExcelProperty` 已不再支持；请使用 `@ExcelColumn`。
