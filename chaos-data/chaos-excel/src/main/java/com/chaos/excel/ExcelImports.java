package com.chaos.excel;

import com.chaos.excel.importer.ErrorPolicy;
import com.chaos.core.importing.ImportRowError;
import com.chaos.excel.importer.ImportOptions;
import com.chaos.excel.importer.ImportPlan;
import com.chaos.excel.importer.ImportResult;
import com.chaos.excel.importer.RowValidationException;
import com.chaos.excel.importer.SheetImportSpec;
import com.chaos.excel.importer.SheetResult;
import com.chaos.excel.internal.HeaderMatcher;
import com.chaos.excel.internal.poi.PoiSheetReader;
import com.chaos.excel.internal.poi.PoiWorkbookInspector;
import com.chaos.excel.internal.poi.PoiZipGuard;
import com.chaos.excel.mapping.CellValue;
import com.chaos.excel.mapping.ColumnBinding;
import com.chaos.excel.mapping.RowMapper;
import com.chaos.excel.mapping.RowMappingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Excel 上传文件暂存、简单读取和多 Sheet 导入的统一入口。 */
public final class ExcelImports {

    private static final int LEGACY_MAX_COLUMNS = 256;

    private ExcelImports() {
    }

    /**
     * 按流式字节上限暂存上传文件并计算 SHA-256；上传流由调用方关闭。
     *
     * @param filename 客户端文件名，可包含浏览器传入的本地路径
     * @param input 上传文件流
     * @param options 允许的扩展名和最大字节数
     * @return 需要通过 {@link StagedFile#close()} 删除临时文件的句柄
     * @throws ExcelProcessingException 文件边界校验、读取、摘要计算或暂存失败时抛出
     */
    public static StagedFile stage(String filename, InputStream input, Options options) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(options, "options must not be null");
        String safeFilename = safeFilename(filename);
        String lowerFilename = safeFilename.toLowerCase(Locale.ROOT);
        if (options.allowedExtensions().stream().noneMatch(lowerFilename::endsWith)) {
            throw new ExcelProcessingException("Excel file extension is not allowed: " + safeFilename);
        }

        Path path = null;
        try {
            path = Files.createTempFile("chaos-excel-import-", ".upload");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            byte[] buffer = new byte[16 * 1024];
            try (OutputStream output = Files.newOutputStream(path)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > options.maxBytes()) {
                        throw new ExcelProcessingException(
                                "Excel file size exceeds limit: " + options.maxBytes() + " bytes");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            if (size == 0) {
                throw new ExcelProcessingException("Excel file content must not be empty");
            }
            return new StagedFile(safeFilename, path, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException exception) {
            deleteTemporaryFile(path, exception);
            throw new ExcelProcessingException("Excel upload staging failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            deleteTemporaryFile(path, exception);
            throw new IllegalStateException("SHA-256 is not available", exception);
        } catch (RuntimeException exception) {
            deleteTemporaryFile(path, exception);
            throw exception;
        }
    }

    /**
     * 按导入计划读取多 Sheet 并聚合错误。
     *
     * @param file 已完成上传校验的暂存文件
     * @param plan 导入计划
     * @return 导入结果
     */
    public static ImportResult execute(StagedFile file, ImportPlan plan) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        ImportOptions options = plan.options();
        if (file.size() > options.maxBytes()) {
            throw new ExcelProcessingException(
                    "Excel file size exceeds limit: " + options.maxBytes() + " bytes");
        }
        PoiZipGuard.check(
                file.path(),
                options.maxUncompressedBytes(),
                options.maxZipEntries(),
                options.maxCompressionRatio());
        List<String> sheetNames = PoiWorkbookInspector.sheetNames(file.path());
        if (sheetNames.size() > options.maxSheets()) {
            throw new ExcelProcessingException(
                    "Excel workbook contains too many sheets: " + sheetNames.size());
        }
        Set<String> sheetsByName = Set.copyOf(sheetNames);
        List<ImportRowError> errors = new ArrayList<>();
        boolean[] truncated = {false};
        Map<String, SheetResult> sheetResults = new LinkedHashMap<>();
        Map<String, List<String>> sheetHeaders = new LinkedHashMap<>();
        long totalRows = 0;
        long successRows = 0;
        long failedRows = 0;
        for (SheetImportSpec<?> spec : plan.sheets()) {
            if (!sheetsByName.contains(spec.sheetName())) {
                if (spec.required()) {
                    throw new ExcelProcessingException("Excel sheet not found: " + spec.sheetName());
                }
                sheetResults.put(spec.sheetName(), new SheetResult(spec.sheetName(), 0, 0, 0, true));
                continue;
            }
            SheetStats stats = importSheet(file.path(), spec, plan, errors, truncated);
            sheetResults.put(spec.sheetName(),
                    new SheetResult(spec.sheetName(), stats.totalRows(),
                            stats.successRows(), stats.failedRows(), false));
            sheetHeaders.put(spec.sheetName(), stats.headers());
            totalRows += stats.totalRows();
            successRows += stats.successRows();
            failedRows += stats.failedRows();
        }
        return new ImportResult(file.filename(), totalRows, successRows, failedRows,
                errors, truncated[0], sheetResults, sheetHeaders);
    }

    private static <T> SheetStats importSheet(
            Path path,
            SheetImportSpec<T> spec,
            ImportPlan plan,
            List<ImportRowError> errors,
            boolean[] truncated) {
        ImportOptions options = plan.options();
        RowMapper<T> mapper = RowMapper.of(spec.rowType());
        List<String> expectedHeaders = mapper.headers();
        long[] total = {0};
        long[] success = {0};
        long[] failed = {0};
        int[] skippedSamples = {0};
        int maxRows = Math.min(spec.maxRows(), options.maxRows());
        int maxErrors = Math.min(plan.errorPolicy().maxErrors(), options.maxErrors());
        PoiSheetReader.read(path, spec.sheetName(), spec.headerRows(), maxRows,
                options.maxColumns(), new PoiSheetReader.RowHandler() {
                    @Override
                    public void header(Map<Integer, String> headers) {
                        HeaderMatcher.validate(headers, expectedHeaders, options.headerPolicy());
                    }

                    @Override
                    public void row(int rowNumber, Map<Integer, CellValue> cells) {
                        if (skippedSamples[0] < spec.skipSampleRows()) {
                            skippedSamples[0]++;
                            return;
                        }
                        total[0]++;
                        Map<Integer, String> rawCells = rawCells(cells);
                        try {
                            T row = mapper.map(cells, rowNumber);
                            spec.validator().validate(row, rowNumber);
                            spec.handler().handle(row, rowNumber);
                            success[0]++;
                        } catch (RowMappingException exception) {
                            handleRowError(plan.errorPolicy(), maxErrors, errors, truncated, new ImportRowError(
                                    spec.sheetName(),
                                    rowNumber,
                                    exception.columnIndex(),
                                    exception.columnName(),
                                    exception.code(),
                                    exception.getMessage(),
                                    rawCells), exception);
                            failed[0]++;
                        } catch (RowValidationException exception) {
                            int columnIndex = columnIndex(mapper, exception.columnName());
                            handleRowError(plan.errorPolicy(), maxErrors, errors, truncated, new ImportRowError(
                                    spec.sheetName(),
                                    rowNumber,
                                    columnIndex,
                                    exception.columnName(),
                                    exception.code(),
                                    exception.getMessage(),
                                    rawCells), exception);
                            failed[0]++;
                        }
                    }
                });
        if (spec.requireData() && total[0] == 0) {
            throw new NoDataException();
        }
        return new SheetStats(total[0], success[0], failed[0], List.copyOf(expectedHeaders));
    }

    private static void handleRowError(
            ErrorPolicy policy,
            int maxErrors,
            List<ImportRowError> errors,
            boolean[] truncated,
            ImportRowError error,
            RuntimeException cause) {
        if (!policy.isCollect()) {
            throw new ExcelProcessingException(
                    "Excel import failed at row " + error.rowNumber() + ": " + error.message(),
                    cause);
        }
        if (errors.size() >= maxErrors) {
            truncated[0] = true;
            return;
        }
        errors.add(error);
    }

    private static Map<Integer, String> rawCells(Map<Integer, CellValue> cells) {
        Map<Integer, String> raw = new LinkedHashMap<>();
        for (Map.Entry<Integer, CellValue> entry : cells.entrySet()) {
            raw.put(entry.getKey(), entry.getValue().displayValue());
        }
        return Map.copyOf(raw);
    }

    private static int columnIndex(RowMapper<?> mapper, String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return -1;
        }
        for (ColumnBinding column : mapper.columns()) {
            if (column.name().equals(columnName)) {
                return column.index();
            }
        }
        return -1;
    }

    /**
     * 按工作簿顺序列出全部 Sheet 名称，供调用方据此构建多 Sheet 导入计划。
     *
     * <p>该方法只解析 workbook.xml，不加载单元格内容；返回结果可用于
     * {@link com.chaos.excel.importer.SheetImportSpec#builder(String, Class)} 或
     * {@link #read} 的工作表序号定位。</p>
     *
     * @param file 已完成上传校验的暂存文件
     * @return 按工作簿顺序排列的不可变 Sheet 名称列表
     * @throws ExcelProcessingException 暂存文件已关闭或工作簿结构无法解析时抛出
     */
    public static List<String> sheetNames(StagedFile file) {
        Objects.requireNonNull(file, "file must not be null");
        return List.copyOf(PoiWorkbookInspector.sheetNames(file.path()));
    }

    /**
     * 按工作簿顺序列出可见 Sheet 名称，跳过隐藏 Sheet。
     *
     * <p>模板生成的 {@code _chaos_dict} 等隐藏页不会出现在结果中，因此该方法适合
     * “读取用户填写的全部工作表”这类场景；需要按名称校验必需 Sheet 时仍可使用
     * {@link #sheetNames(StagedFile)}。</p>
     *
     * @param file 已完成上传校验的暂存文件
     * @return 按工作簿顺序排列的不可变可见 Sheet 名称列表
     * @throws ExcelProcessingException 暂存文件已关闭或工作簿结构无法解析时抛出
     */
    public static List<String> visibleSheetNames(StagedFile file) {
        Objects.requireNonNull(file, "file must not be null");
        return List.copyOf(PoiWorkbookInspector.visibleSheetNames(file.path()));
    }

    /**
     * 读取暂存工作簿的全部数据行；结果会保存在内存中。
     *
     * @param file 已完成上传校验的暂存文件
     * @param rowType 行模型类型
     * @param options 工作表、表头和行数限制
     * @param <T> 行模型类型
     * @return 按工作表顺序生成的不可变数据行列表
     */
    public static <T> List<T> read(StagedFile file, Class<T> rowType, ReadOptions options) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(options, "options must not be null");
        String sheetName = sheetName(file, options.sheetNumber());
        List<T> rows = new ArrayList<>();
        readMapped(file, sheetName, rowType, options.headerRows(), options.maxRows(),
                options.batchSize(), (batch, numbers) -> rows.addAll(batch));
        return List.copyOf(rows);
    }

    /**
     * 使用默认限制读取暂存工作簿的第一张工作表；结果会保存在内存中。
     *
     * @param file 已完成上传校验的暂存文件
     * @param rowType 行模型类型
     * @param <T> 行模型类型
     * @return 第一工作表中的不可变数据行列表
     */
    public static <T> List<T> read(StagedFile file, Class<T> rowType) {
        return read(file, rowType, ReadOptions.defaults());
    }

    /**
     * 按配置批次读取暂存工作簿；框架负责打开和关闭解析流。
     *
     * @param file 已完成上传校验的暂存文件
     * @param rowType 行模型类型
     * @param options 工作表、表头、批次和行数限制
     * @param batchConsumer 按工作表顺序接收不可变批次的处理器
     * @param <T> 行模型类型
     */
    public static <T> void readBatches(
            StagedFile file, Class<T> rowType, ReadOptions options, Consumer<List<T>> batchConsumer) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(batchConsumer, "batchConsumer must not be null");
        String sheetName = sheetName(file, options.sheetNumber());
        readMapped(file, sheetName, rowType, options.headerRows(), options.maxRows(),
                options.batchSize(), (batch, numbers) -> batchConsumer.accept(batch));
    }

    private static <T> void readMapped(
            StagedFile file,
            String sheetName,
            Class<T> rowType,
            int headerRows,
            int maxRows,
            int batchSize,
            BiConsumer<List<T>, int[]> batchConsumer) {
        RowMapper<T> mapper = RowMapper.of(rowType);
        List<T> batch = new ArrayList<>(Math.max(1, batchSize));
        List<Integer> numbers = new ArrayList<>(Math.max(1, batchSize));
        PoiSheetReader.read(file.path(), sheetName, headerRows, maxRows,
                LEGACY_MAX_COLUMNS, new PoiSheetReader.RowHandler() {
                    @Override
                    public void row(int rowNumber, Map<Integer, CellValue> cells) {
                        batch.add(mapper.map(cells, rowNumber));
                        numbers.add(rowNumber);
                        if (batch.size() >= batchSize) {
                            flush(batch, numbers, batchConsumer);
                        }
                    }
                });
        flush(batch, numbers, batchConsumer);
    }

    private static <T> void flush(
            List<T> batch, List<Integer> numbers, BiConsumer<List<T>, int[]> batchConsumer) {
        if (batch.isEmpty()) {
            return;
        }
        int[] rowNumbers = numbers.stream().mapToInt(Integer::intValue).toArray();
        batchConsumer.accept(List.copyOf(batch), rowNumbers);
        batch.clear();
        numbers.clear();
    }

    private static String sheetName(StagedFile file, int sheetNumber) {
        List<String> sheets = PoiWorkbookInspector.sheetNames(file.path());
        if (sheetNumber < 0 || sheetNumber >= sheets.size()) {
            throw new ExcelProcessingException("Excel sheet index out of range: " + sheetNumber);
        }
        return sheets.get(sheetNumber);
    }

    /**
     * 通过统一解析器读取暂存文件并要求至少包含一条数据行。
     *
     * @param file 已完成上传边界校验和摘要计算的暂存文件
     * @param rowType 行模型类型
     * @param options 工作表、表头、批次和最大行数限制
     * @param <T> 行模型类型
     * @return 包含文件元数据和不可变数据行的结果
     */
    public static <T> StagedData<T> readRequired(
            StagedFile file, Class<T> rowType, ReadOptions options) {
        List<T> rows = read(file, rowType, options);
        if (rows.isEmpty()) {
            throw new NoDataException();
        }
        return new StagedData<>(file, rows);
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ExcelProcessingException("Excel filename must not be blank");
        }
        String normalized = filename.strip().replace('\\', '/');
        String safeFilename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (safeFilename.isBlank()) {
            throw new ExcelProcessingException("Excel filename must not be blank");
        }
        return safeFilename;
    }

    private static void deleteTemporaryFile(Path path, Throwable failure) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }

    private record SheetStats(long totalRows, long successRows, long failedRows, List<String> headers) {
    }

    /** 导入文件扩展名和大小限制。 */
    public record Options(Set<String> allowedExtensions, int maxBytes) {
        public Options {
            Objects.requireNonNull(allowedExtensions, "allowedExtensions must not be null");
            if (allowedExtensions.isEmpty()) {
                throw new IllegalArgumentException("allowedExtensions must not be empty");
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String extension : allowedExtensions) {
                if (extension == null || extension.isBlank()) {
                    throw new IllegalArgumentException("allowedExtensions must not contain blank values");
                }
                String value = extension.toLowerCase(Locale.ROOT);
                normalized.add(value.startsWith(".") ? value : "." + value);
            }
            allowedExtensions = Set.copyOf(normalized);
            if (maxBytes < 1) {
                throw new IllegalArgumentException("maxBytes must be greater than 0");
            }
        }

        /**
         * 创建仅允许 XLSX 的文件策略。
         *
         * @param maxBytes 文件最大字节数
         * @return XLSX 导入策略
         */
        public static Options xlsx(int maxBytes) {
            return new Options(Set.of(".xlsx"), maxBytes);
        }
    }

    /** Excel 解析时的工作表、表头、批次和行数限制。 */
    public record ReadOptions(int sheetNumber, int headerRows, int batchSize, int maxRows) {
        public static final int DEFAULT_BATCH_SIZE = 500;
        public static final int DEFAULT_MAX_ROWS = 100_000;

        public ReadOptions {
            if (sheetNumber < 0) {
                throw new IllegalArgumentException("sheetNumber must be at least 0");
            }
            if (headerRows < 0) {
                throw new IllegalArgumentException("headerRows must be at least 0");
            }
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize must be greater than 0");
            }
            if (maxRows < 1) {
                throw new IllegalArgumentException("maxRows must be greater than 0");
            }
        }

        /** @return 第一工作表、单行表头、每批 500 行且最多 100,000 行的默认配置 */
        public static ReadOptions defaults() {
            return new ReadOptions(0, 1, DEFAULT_BATCH_SIZE, DEFAULT_MAX_ROWS);
        }
    }

    /** 通过流式校验暂存的导入文件；关闭句柄会删除临时内容。 */
    public static final class StagedFile implements AutoCloseable {
        private final String filename;
        private final Path path;
        private final long size;
        private final String sha256;
        private boolean closed;

        private StagedFile(String filename, Path path, long size, String sha256) {
            this.filename = filename;
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
        }

        /** @return 清除客户端本地路径后的文件名 */
        public String filename() {
            return filename;
        }

        /** @return 文件字节数 */
        public long size() {
            return size;
        }

        /** @return 原始文件字节的 SHA-256 小写十六进制值 */
        public String sha256() {
            return sha256;
        }

        /**
         * 返回暂存路径，仅供框架内部解析使用。
         *
         * @return 暂存文件路径
         */
        public synchronized Path path() {
            if (closed) {
                throw new ExcelProcessingException("Staged Excel file is already closed");
            }
            return path;
        }

        /**
         * 打开一条读取流；关闭句柄后不可再打开。
         *
         * @return 由调用方关闭的文件流
         */
        synchronized InputStream openStream() {
            if (closed) {
                throw new ExcelProcessingException("Staged Excel file is already closed");
            }
            try {
                return Files.newInputStream(path);
            } catch (IOException exception) {
                throw new ExcelProcessingException("Staged Excel file cannot be read", exception);
            }
        }

        /**
         * 删除暂存文件；重复调用不重复清理。
         *
         * @throws IOException 临时文件无法删除时抛出
         */
        @Override
        public synchronized void close() throws IOException {
            if (!closed) {
                Files.deleteIfExists(path);
                closed = true;
            }
        }
    }

    /** 从暂存文件读取的结果。 */
    public record StagedData<T>(StagedFile file, List<T> rows) {
        public StagedData {
            Objects.requireNonNull(file, "file must not be null");
            rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
        }
    }

    /** 工作簿没有任何数据行。 */
    public static final class NoDataException extends ExcelProcessingException {
        public NoDataException() {
            super("Excel workbook must contain at least one data row");
        }
    }
}
