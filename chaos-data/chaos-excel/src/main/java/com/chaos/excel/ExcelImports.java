package com.chaos.excel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Excel 上传校验、摘要计算、必填数据读取和逐行处理的统一入口。 */
public final class ExcelImports {

    private ExcelImports() {
    }

    /**
     * 校验上传文件并返回安全文件名、内容和 SHA-256 摘要。
     *
     * @param filename 客户端上传文件名，允许包含浏览器传入的本地路径
     * @param content 文件完整字节内容，不能为 {@code null} 或空数组
     * @param options 允许扩展名和最大内容字节数
     * @return 包含安全文件名、防御性复制内容和摘要的导入文件
     * @throws ExcelProcessingException 文件名、扩展名或内容不符合导入约束时抛出
     */
    public static File prepare(String filename, byte[] content, Options options) {
        Objects.requireNonNull(options, "options must not be null");
        String safeFilename = safeFilename(filename);
        String lowerFilename = safeFilename.toLowerCase(Locale.ROOT);
        boolean supported = options.allowedExtensions().stream().anyMatch(lowerFilename::endsWith);
        if (!supported) {
            throw new ExcelProcessingException(
                    "Excel file extension is not allowed: " + safeFilename);
        }
        if (content == null || content.length == 0) {
            throw new ExcelProcessingException("Excel file content must not be empty");
        }
        if (content.length > options.maxBytes()) {
            throw new ExcelProcessingException(
                    "Excel file size exceeds limit: " + options.maxBytes() + " bytes");
        }
        return new File(safeFilename, content, sha256(content));
    }

    /**
     * 校验上传文件并使用调用方提供的映射器读取至少一行数据。
     *
     * @param filename 客户端上传文件名，允许包含浏览器传入的本地路径
     * @param content 文件完整字节内容
     * @param options 文件扩展名和内容大小限制
     * @param reader 读取已校验文件内容并返回映射行的函数
     * @param <T> 单行映射类型
     * @return 包含安全文件信息和不可变数据行的导入数据
     * @throws ExcelProcessingException 文件校验、读取失败或工作簿没有数据行时抛出
     */
    public static <T> Data<T> readRequired(
            String filename,
            byte[] content,
            Options options,
            Function<byte[], List<T>> reader) {
        return readRequired(prepare(filename, content, options), reader);
    }

    /**
     * 使用调用方提供的映射器读取已校验文件中的至少一行数据。
     *
     * @param file 已完成上传校验和摘要计算的文件
     * @param reader 读取文件内容并返回映射行的函数
     * @param <T> 单行映射类型
     * @return 包含原文件信息和不可变数据行的导入数据
     * @throws ExcelProcessingException 文件读取失败或工作簿没有数据行时抛出
     */
    public static <T> Data<T> readRequired(File file, Function<byte[], List<T>> reader) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(reader, "reader must not be null");
        List<T> rows = Objects.requireNonNull(
                reader.apply(file.content()), "reader result must not be null");
        if (rows.isEmpty()) {
            throw new NoDataException();
        }
        return new Data<>(file, rows);
    }

    /**
     * 校验上传文件并使用 FastExcel 映射类型读取至少一行数据。
     *
     * @param filename 客户端上传文件名，允许包含浏览器传入的本地路径
     * @param content 文件完整字节内容
     * @param rowType FastExcel 单行映射类型
     * @param importOptions 文件扩展名和内容大小限制
     * @param readOptions 工作表、表头、批次和最大行数限制
     * @param <T> 单行映射类型
     * @return 包含安全文件信息和不可变数据行的导入数据
     * @throws ExcelProcessingException 文件校验、解析或数据行校验失败时抛出
     */
    public static <T> Data<T> readRequired(
            String filename,
            byte[] content,
            Class<T> rowType,
            Options importOptions,
            ExcelReadOptions readOptions) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(readOptions, "readOptions must not be null");
        return readRequired(
                filename,
                content,
                importOptions,
                bytes -> ExcelOperations.read(bytes, rowType, readOptions));
    }

    /**
     * 读取至少一行数据并按顺序处理每行，同时为处理异常补充真实 Excel 行号。
     *
     * @param file 已完成上传校验和摘要计算的文件
     * @param reader 读取文件内容并返回映射行的函数
     * @param firstDataRowNumber 第一条数据在 Excel 中的一基行号
     * @param processor 单行处理函数，返回值不能为 {@code null}
     * @param <T> 单行映射类型
     * @param <R> 单行处理结果类型
     * @return 包含原文件信息和不可变行处理结果的导入数据
     * @throws ExcelProcessingException 文件读取失败、没有数据行或单行处理失败时抛出
     */
    public static <T, R> Data<R> processRequired(
            File file,
            Function<byte[], List<T>> reader,
            int firstDataRowNumber,
            Function<T, R> processor) {
        Data<T> data = readRequired(file, reader);
        return new Data<>(data.file(), map(data.rows(), firstDataRowNumber, processor));
    }

    private static <T, R> List<R> map(
            List<T> rows, int firstDataRowNumber, Function<T, R> processor) {
        Objects.requireNonNull(processor, "processor must not be null");
        if (firstDataRowNumber < 1) {
            throw new IllegalArgumentException("firstDataRowNumber must be positive");
        }
        List<R> results = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            try {
                results.add(Objects.requireNonNull(
                        processor.apply(rows.get(index)), "processor result must not be null"));
            } catch (RowProcessingException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new RowProcessingException(firstDataRowNumber + index, exception);
            }
        }
        return List.copyOf(results);
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

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** 通过上传校验并计算摘要的 Excel 导入文件。 */
    public record File(String filename, byte[] content, String sha256) {

        public File {
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("filename must not be blank");
            }
            content = Objects.requireNonNull(content, "content must not be null").clone();
            if (content.length == 0) {
                throw new IllegalArgumentException("content must not be empty");
            }
            if (sha256 == null || sha256.isBlank()) {
                throw new IllegalArgumentException("sha256 must not be blank");
            }
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    /** 已完成文件校验、数据读取或逐行处理的导入数据。 */
    public record Data<T>(File file, List<T> rows) {

        public Data {
            Objects.requireNonNull(file, "file must not be null");
            rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
        }
    }

    /** Excel 上传文件校验选项。 */
    public record Options(Set<String> allowedExtensions, int maxBytes) {

        public Options {
            Objects.requireNonNull(allowedExtensions, "allowedExtensions must not be null");
            if (allowedExtensions.isEmpty()) {
                throw new IllegalArgumentException("allowedExtensions must not be empty");
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String extension : allowedExtensions) {
                if (extension == null || extension.isBlank()) {
                    throw new IllegalArgumentException(
                            "allowedExtensions must not contain blank values");
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
         * 创建仅允许 XLSX 文件的校验选项。
         *
         * @param maxBytes 文件内容允许占用的最大字节数
         * @return 仅接受 {@code .xlsx} 扩展名的不可变选项
         */
        public static Options xlsx(int maxBytes) {
            return new Options(Set.of(".xlsx"), maxBytes);
        }
    }

    /** 工作簿没有任何数据行。 */
    public static final class NoDataException extends ExcelProcessingException {

        public NoDataException() {
            super("Excel workbook must contain at least one data row");
        }
    }

    /** 单行处理失败并携带用户可定位的 Excel 行号。 */
    public static final class RowProcessingException extends ExcelProcessingException {
        private final int rowNumber;

        /**
         * 创建包含一基 Excel 行号的处理异常。
         *
         * @param rowNumber 从 1 开始的 Excel 行号
         * @param cause 单行处理产生的原始异常
         */
        public RowProcessingException(int rowNumber, RuntimeException cause) {
            super("Excel row processing failed at row " + requirePositive(rowNumber),
                    Objects.requireNonNull(cause, "cause must not be null"));
            this.rowNumber = rowNumber;
        }

        /**
         * 返回用户在工作簿中可直接定位的一基行号。
         *
         * @return 从 1 开始的 Excel 行号
         */
        public int rowNumber() {
            return rowNumber;
        }

        /**
         * 返回单行处理产生的原始运行时异常。
         *
         * @return 业务校验或数据处理产生的原始异常
         */
        public RuntimeException originalCause() {
            return (RuntimeException) getCause();
        }

        private static int requirePositive(int rowNumber) {
            if (rowNumber < 1) {
                throw new IllegalArgumentException("rowNumber must be positive");
            }
            return rowNumber;
        }
    }
}
