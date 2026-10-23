package com.chaos.excel;

import com.chaos.excel.export.ExportOptions;
import com.chaos.excel.export.ExportSheet;
import com.chaos.excel.internal.poi.PoiWorkbookWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * XLSX 导出统一门面。
 *
 * <p>根据文件规模和响应方式，调用方可以选择内存文档、直接写入输出流或延迟批量导出。
 * 本类不关闭调用方传入的输出流。</p>
 */
public final class ExcelExports {

    private ExcelExports() {
    }

    /**
     * 在内存中生成单 Sheet 工作簿。
     *
     * @param filename 下载文件名，可省略 {@code .xlsx} 后缀
     * @param rowType 行模型类型
     * @param rows 工作簿数据行
     * @param options 工作表名称和表头配置
     * @param <T> 工作簿行模型
     * @return 包含完整 XLSX 字节的不可变内存文档
     */
    public static <T> ExcelDocument write(
            String filename, Class<T> rowType, Collection<T> rows, WriteOptions options) {
        return write(filename,
                List.of(ExportSheet.of(options.sheetName(), rowType, List.of(rows))),
                ExportOptions.defaults().includeHeader(options.includeHeader()));
    }

    /**
     * 使用默认工作表和表头配置在内存中生成单 Sheet 工作簿。
     *
     * @param filename 下载文件名，可省略 {@code .xlsx} 后缀
     * @param rowType 行模型类型
     * @param rows 工作簿数据行
     * @param <T> 工作簿行模型
     * @return 包含完整 XLSX 字节的不可变内存文档
     */
    public static <T> ExcelDocument write(String filename, Class<T> rowType, Collection<T> rows) {
        return write(filename, rowType, rows, WriteOptions.defaults());
    }

    /**
     * 在内存中生成多 Sheet 工作簿。
     *
     * @param filename 下载文件名，可省略 {@code .xlsx} 后缀
     * @param sheets 多 Sheet 定义
     * @param options 导出选项
     * @return 包含完整 XLSX 字节的不可变内存文档
     */
    public static ExcelDocument write(
            String filename, List<ExportSheet> sheets, ExportOptions options) {
        Objects.requireNonNull(sheets, "sheets must not be null");
        Objects.requireNonNull(options, "options must not be null");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PoiWorkbookWriter.write(output, sheets, options);
        return ExcelDocument.xlsx(filename, output.toByteArray());
    }

    /**
     * 将单 Sheet 数据行直接写入调用方拥有的输出流，方法不会关闭该输出流。
     *
     * @param output 目标输出流，生命周期由调用方管理
     * @param rowType 行模型类型
     * @param rows 工作簿数据行
     * @param options 工作表名称和表头配置
     * @param <T> 工作簿行模型
     */
    public static <T> void write(
            OutputStream output, Class<T> rowType, Collection<T> rows, WriteOptions options) {
        Objects.requireNonNull(output, "output must not be null");
        PoiWorkbookWriter.write(output,
                List.of(ExportSheet.of(options.sheetName(), rowType, List.of(rows))),
                ExportOptions.defaults().includeHeader(options.includeHeader()));
    }

    /**
     * 将有界数据批次直接写入调用方拥有的输出流，方法不会关闭该输出流。
     *
     * @param output 目标输出流，生命周期由调用方管理
     * @param rowType 行模型类型
     * @param batches 提供有界数据行集合的批次迭代器
     * @param options 工作表名称和表头配置
     * @param <T> 工作簿行模型
     */
    public static <T> void writeBatches(
            OutputStream output,
            Class<T> rowType,
            Iterable<? extends Collection<T>> batches,
            WriteOptions options) {
        Objects.requireNonNull(output, "output must not be null");
        PoiWorkbookWriter.write(output,
                List.of(ExportSheet.of(options.sheetName(), rowType, batches)),
                ExportOptions.defaults().includeHeader(options.includeHeader()));
    }

    /**
     * 创建延迟导出文件，仅在调用 {@link ExcelFile#writeTo(OutputStream)} 时读取数据批次。
     *
     * @param filename 下载文件名，可省略 {@code .xlsx} 后缀
     * @param rowType 行模型类型
     * @param batchSource 每次写出时提供有界数据批次的工厂；工厂不会在本方法调用时执行
     * @param options 工作表名称和表头配置
     * @param <T> 工作簿行模型
     * @return 不缓存完整工作簿、可直接写入响应流的导出文件
     */
    public static <T> StreamingExcelFile exportBatches(
            String filename,
            Class<T> rowType,
            Supplier<? extends Iterable<? extends Collection<T>>> batchSource,
            WriteOptions options) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(batchSource, "batchSource must not be null");
        Objects.requireNonNull(options, "options must not be null");
        return new StreamingExcelFile(filename, output -> PoiWorkbookWriter.write(
                output,
                List.of(ExportSheet.of(options.sheetName(), rowType,
                        Objects.requireNonNull(batchSource.get(), "batch source result must not be null"))),
                ExportOptions.defaults().includeHeader(options.includeHeader())));
    }

    /** XLSX 工作表名称和表头配置。 */
    public record WriteOptions(String sheetName, boolean includeHeader) {
        public WriteOptions {
            if (sheetName == null || sheetName.isBlank()) {
                throw new IllegalArgumentException("sheetName must not be blank");
            }
            if (sheetName.length() > 31) {
                throw new IllegalArgumentException("sheetName must not exceed 31 characters");
            }
        }

        /**
         * 返回默认导出配置。
         *
         * @return 使用 {@code Sheet1} 工作表名称并包含表头的配置
         */
        public static WriteOptions defaults() {
            return new WriteOptions("Sheet1", true);
        }
    }
}
