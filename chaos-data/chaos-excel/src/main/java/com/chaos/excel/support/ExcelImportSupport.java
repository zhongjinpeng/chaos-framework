package com.chaos.excel.support;

import com.chaos.core.importing.ImportRejectedException;
import com.chaos.core.importing.ImportResult;
import com.chaos.core.importing.ImportRowHandler;
import com.chaos.core.importing.ImportRowValidationException;
import com.chaos.core.importing.ImportRowValidator;
import com.chaos.core.importing.ImportSource;
import com.chaos.excel.ExcelImports;
import com.chaos.excel.ExcelProcessingException;
import com.chaos.excel.RowLimitExceededException;
import com.chaos.excel.importer.ErrorPolicy;
import com.chaos.excel.importer.HeaderMismatchException;
import com.chaos.excel.importer.ImportPlan;
import com.chaos.excel.importer.RowValidationException;
import com.chaos.excel.importer.SheetImportSpec;

import java.io.InputStream;
import java.util.List;

/**
 * 基于 Chaos Excel 框架的导入执行适配器。
 *
 * <p>本类负责三件事：把上传流暂存为可重复读取的文件、执行应用层定义的导入计划、把框架的
 * 结果与异常翻译成应用层契约（{@link ImportResult} 与 {@link ImportRejectedException}）。
 * 应用层因此不需要 import 任何 Excel 框架类型。</p>
 */
public final class ExcelImportSupport {

    private ExcelImportSupport() {}

    /**
     * 按流式字节上限暂存上传文件并计算 SHA-256；上传流由调用方关闭。
     *
     * @param filename 客户端文件名，可包含浏览器传入的本地路径
     * @param input 上传文件流
     * @param maxBytes 允许的最大字节数
     * @return 需要通过 {@link ImportSource#close()} 删除临时文件的导入来源
     * @throws ImportRejectedException 扩展名、文件大小、内容或暂存失败时抛出
     */
    public static ImportSource stage(String filename, InputStream input, long maxBytes) {
        try {
            return StagedImportSource.wrap(
                    ExcelImports.stage(
                            filename, input, ExcelImports.Options.xlsx(Math.toIntExact(maxBytes))));
        } catch (ExcelProcessingException ex) {
            throw new ImportRejectedException(
                    ImportRejectedException.Reason.INVALID_FILE, "导入文件不合法");
        }
    }

    /**
     * 执行导入计划，并把框架结果翻译为应用层导入结果。
     *
     * @param source 已完成上传校验的导入来源
     * @param plan 由基础设施适配器构建的导入计划
     * @return 数据行数、成功失败行数、错误明细和错误文件
     * @throws ImportRejectedException 表头不匹配、数据行超限或工作簿无法解析时抛出
     */
    public static ImportResult execute(ImportSource source, ImportPlan plan) {
        try {
            com.chaos.excel.importer.ImportResult result =
                    ExcelImports.execute(StagedImportSource.of(source).stagedFile(), plan);
            return new ImportResult(
                    result.totalRows(),
                    result.successRows(),
                    result.failedRows(),
                    result.errors(),
                    result.errorsTruncated(),
                    result.errorFile().map(ExcelFiles::toAttachment).orElse(null));
        } catch (HeaderMismatchException ex) {
            throw new ImportRejectedException(
                    ImportRejectedException.Reason.HEADER_MISMATCH, "导入文件表头与模板不一致");
        } catch (RowLimitExceededException ex) {
            throw new ImportRejectedException(
                    ImportRejectedException.Reason.ROW_LIMIT_EXCEEDED,
                    "导入数据行数超过单次上限",
                    ex.maxRows());
        } catch (ExcelProcessingException ex) {
            throw new ImportRejectedException(ImportRejectedException.Reason.UNREADABLE, "无法解析工作簿");
        }
    }

    /**
     * 构建单 Sheet 导入计划，统一单行表头、无数据不失败、行数上限和错误收集策略。
     *
     * @param <T> 行模型类型
     * @param sheetName 工作表名称
     * @param rowType 行模型类型
     * @param maxRows 最大数据行数
     * @param maxErrors 最多保留的错误条数
     * @param validator 行级校验器
     * @param handler 行级处理器
     * @return 可直接交给 {@link #execute(ImportSource, ImportPlan)} 的导入计划
     */
    public static <T> ImportPlan singleSheetPlan(
            String sheetName,
            Class<T> rowType,
            int maxRows,
            int maxErrors,
            ImportRowValidator<T> validator,
            ImportRowHandler<T> handler) {
        return singleSheetPlan(
                sheetName, rowType, maxRows, ErrorPolicy.collect(maxErrors), validator, handler);
    }

    /**
     * 构建不执行额外校验的单 Sheet 导入计划。
     *
     * @param <T> 行模型类型
     * @param sheetName 工作表名称
     * @param rowType 行模型类型
     * @param maxRows 最大数据行数
     * @param maxErrors 最多保留的错误条数
     * @param handler 行级处理器
     * @return 可直接交给 {@link #execute(ImportSource, ImportPlan)} 的导入计划
     */
    public static <T> ImportPlan singleSheetPlan(
            String sheetName,
            Class<T> rowType,
            int maxRows,
            int maxErrors,
            ImportRowHandler<T> handler) {
        return singleSheetPlan(
                sheetName, rowType, maxRows, maxErrors, (row, rowNumber) -> {}, handler);
    }

    /**
     * 构建使用指定错误策略的单 Sheet 导入计划。
     *
     * @param <T> 行模型类型
     * @param sheetName 工作表名称
     * @param rowType 行模型类型
     * @param maxRows 最大数据行数
     * @param errorPolicy 行级错误策略；快速失败场景使用 {@link ErrorPolicy#failFast()}
     * @param validator 行级校验器
     * @param handler 行级处理器
     * @return 可直接交给 {@link #execute(ImportSource, ImportPlan)} 的导入计划
     */
    public static <T> ImportPlan singleSheetPlan(
            String sheetName,
            Class<T> rowType,
            int maxRows,
            ErrorPolicy errorPolicy,
            ImportRowValidator<T> validator,
            ImportRowHandler<T> handler) {
        return ImportPlan.builder()
                .sheet(sheetSpec(sheetName, rowType, maxRows, validator, handler))
                .errorPolicy(errorPolicy)
                .build();
    }

    /**
     * 构建使用指定错误策略且不执行额外校验的单 Sheet 导入计划。
     *
     * @param <T> 行模型类型
     * @param sheetName 工作表名称
     * @param rowType 行模型类型
     * @param maxRows 最大数据行数
     * @param errorPolicy 行级错误策略
     * @param handler 行级处理器
     * @return 可直接交给 {@link #execute(ImportSource, ImportPlan)} 的导入计划
     */
    public static <T> ImportPlan singleSheetPlan(
            String sheetName,
            Class<T> rowType,
            int maxRows,
            ErrorPolicy errorPolicy,
            ImportRowHandler<T> handler) {
        return singleSheetPlan(
                sheetName, rowType, maxRows, errorPolicy, (row, rowNumber) -> {}, handler);
    }

    /**
     * 构建严格表头的单 Sheet 定义，供多 Sheet 导入计划复用。
     *
     * @param <T> 行模型类型
     * @param sheetName 工作表名称
     * @param rowType 行模型类型
     * @param maxRows 最大数据行数
     * @param validator 行级校验器
     * @param handler 行级处理器
     * @return 按行模型注解校验表头的 Sheet 定义
     */
    public static <T> SheetImportSpec<T> sheetSpec(
            String sheetName,
            Class<T> rowType,
            int maxRows,
            ImportRowValidator<T> validator,
            ImportRowHandler<T> handler) {
        return SheetImportSpec.builder(sheetName, rowType)
                .headerRows(1)
                .maxRows(maxRows)
                .requireData(false)
                .validator(
                        (data, rowNumber) ->
                                runRowAction(() -> validator.validate(data, rowNumber)))
                .handler((data, rowNumber) -> runRowAction(() -> handler.handle(data, rowNumber)))
                .build();
    }

    /**
     * 构建严格表头且不执行额外校验的单 Sheet 定义。
     *
     * @param <T> 行模型类型
     * @param sheetName 工作表名称
     * @param rowType 行模型类型
     * @param maxRows 最大数据行数
     * @param handler 行级处理器
     * @return 按行模型注解校验表头的 Sheet 定义
     */
    public static <T> SheetImportSpec<T> sheetSpec(
            String sheetName, Class<T> rowType, int maxRows, ImportRowHandler<T> handler) {
        return sheetSpec(sheetName, rowType, maxRows, (row, rowNumber) -> {}, handler);
    }

    /**
     * 按工作簿顺序列出全部工作表名称，供调用方构建多 Sheet 导入计划。
     *
     * @param source 已完成上传校验的导入来源
     * @return 按工作簿顺序排列的不可变工作表名称列表
     * @throws com.chaos.excel.ExcelProcessingException 暂存文件已关闭或工作簿结构无法解析时抛出
     */
    public static List<String> sheetNames(ImportSource source) {
        return ExcelImports.sheetNames(StagedImportSource.of(source).stagedFile());
    }

    /**
     * 按工作簿顺序列出可见工作表名称，跳过隐藏的模板字典页。
     *
     * @param source 已完成上传校验的导入来源
     * @return 按工作簿顺序排列的不可变可见工作表名称列表
     * @throws com.chaos.excel.ExcelProcessingException 暂存文件已关闭或工作簿结构无法解析时抛出
     */
    public static List<String> visibleSheetNames(ImportSource source) {
        return ExcelImports.visibleSheetNames(StagedImportSource.of(source).stagedFile());
    }

    /**
     * 执行单行动作，并把应用层行级校验异常翻译为框架可收集的行级错误。
     *
     * @param action 校验或处理单行的动作
     */
    public static void runRowAction(Runnable action) {
        try {
            action.run();
        } catch (ImportRowValidationException ex) {
            throw new RowValidationException(ex.code(), ex.columnName(), ex.getMessage());
        }
    }
}
