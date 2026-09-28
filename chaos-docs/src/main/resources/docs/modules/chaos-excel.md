# chaos-excel

## 职责

`chaos-excel` 基于 FastExcel 提供统一的 XLSX 导入导出入口，不依赖 Spring MVC 或 Servlet。
Controller 负责上传、下载协议，业务服务负责数据校验与持久化，本模块只负责表格读写和资源边界。

## 依赖方式

```xml
<dependency>
    <groupId>com.chaos</groupId>
    <artifactId>chaos-excel</artifactId>
</dependency>
```

业务行模型使用 FastExcel 的 `@ExcelProperty` 声明表头和列顺序。

## 导出

```java
ExcelDocument document = ExcelOperations.write(
        "监测记录",
        SurveyExportRow.class,
        rows,
        new ExcelWriteOptions("监测记录", true));
```

`ExcelDocument` 会把文件名规范为 `.xlsx` 并提供标准 MIME 类型。需要直接写 HTTP 响应或文件流时，
调用接收 `OutputStream` 的重载；该方法不会关闭调用方传入的流。

## 导入

小文件可以一次读取：

```java
List<SurveyImportRow> rows = ExcelOperations.read(content, SurveyImportRow.class);
```

生产导入应使用批处理接口，避免把整个工作簿加载到内存：

```java
ExcelReadOptions options = new ExcelReadOptions(0, 1, 500, 100_000);
ExcelOperations.readBatches(inputStream, SurveyImportRow.class, options, batch -> {
    applicationService.importBatch(batch);
});
```

默认读取第一个工作表、跳过一行表头、每 500 行回调一次，最多读取 100,000 行。超过上限、
单元格转换失败或文件损坏时抛出 `ExcelProcessingException`。输入流同样由调用方关闭。

## 边界

- 只处理 XLSX/Excel 数据映射，不包含数据库事务、租户选择和业务校验。
- 批处理回调应自行确定事务粒度；不要对整个大文件开启单个数据库事务。
- 文件大小限制应在上传层执行，行数限制由 `ExcelReadOptions.maxRows` 执行。
