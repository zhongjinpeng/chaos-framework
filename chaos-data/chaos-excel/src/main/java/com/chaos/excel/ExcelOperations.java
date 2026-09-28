package com.chaos.excel;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import cn.idev.excel.exception.ExcelDataConvertException;
import cn.idev.excel.read.builder.ExcelReaderBuilder;
import cn.idev.excel.write.metadata.WriteSheet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** FastExcel 的统一、安全读写入口。 */
public final class ExcelOperations {
    private ExcelOperations() {
    }

    public static <T> ExcelDocument write(String filename, Class<T> rowType, Collection<T> rows) {
        return write(filename, rowType, rows, ExcelWriteOptions.defaults());
    }

    public static <T> ExcelDocument write(String filename, Class<T> rowType, Collection<T> rows,
                                          ExcelWriteOptions options) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, rowType, rows, options);
        return ExcelDocument.xlsx(filename, output.toByteArray());
    }

    public static <T> void write(OutputStream output, Class<T> rowType, Collection<T> rows,
                                 ExcelWriteOptions options) {
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(options, "options must not be null");
        try {
            FastExcel.write(output, rowType)
                    .autoCloseStream(false)
                    .needHead(options.includeHeader())
                    .sheet(options.sheetName())
                    .doWrite(rows);
        } catch (RuntimeException ex) {
            throw wrap("Excel export failed", ex);
        }
    }

    public static <T> void writeBatches(OutputStream output, Class<T> rowType,
                                        Iterable<? extends Collection<T>> batches,
                                        ExcelWriteOptions options) {
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(batches, "batches must not be null");
        Objects.requireNonNull(options, "options must not be null");
        try (ExcelWriter writer = FastExcel.write(output, rowType).autoCloseStream(false)
                .needHead(options.includeHeader()).build()) {
            WriteSheet sheet = FastExcel.writerSheet(options.sheetName()).build();
            for (Collection<T> batch : batches) {
                writer.write(Objects.requireNonNull(batch, "batch must not be null"), sheet);
            }
        } catch (RuntimeException ex) {
            throw wrap("Excel export failed", ex);
        }
    }

    public static <T> List<T> read(byte[] content, Class<T> rowType) {
        Objects.requireNonNull(content, "content must not be null");
        return read(new ByteArrayInputStream(content), rowType, ExcelReadOptions.defaults());
    }

    public static <T> List<T> read(InputStream input, Class<T> rowType, ExcelReadOptions options) {
        List<T> rows = new ArrayList<>();
        readBatches(input, rowType, options, rows::addAll);
        return List.copyOf(rows);
    }

    public static <T> void readBatches(InputStream input, Class<T> rowType, ExcelReadOptions options,
                                       Consumer<List<T>> batchConsumer) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(batchConsumer, "batchConsumer must not be null");
        BatchReadListener<T> listener = new BatchReadListener<>(options, batchConsumer);
        try {
            ExcelReaderBuilder reader = FastExcel.read(input, rowType, listener)
                    .autoCloseStream(false)
                    .headRowNumber(options.headerRows());
            reader.sheet(options.sheetNumber()).doRead();
        } catch (RuntimeException ex) {
            throw wrap("Excel import failed", ex);
        }
    }

    private static ExcelProcessingException wrap(String message, RuntimeException cause) {
        if (cause instanceof ExcelProcessingException exception) {
            return exception;
        }
        return new ExcelProcessingException(message, cause);
    }

    private static final class BatchReadListener<T> extends AnalysisEventListener<T> {
        private final ExcelReadOptions options;
        private final Consumer<List<T>> batchConsumer;
        private final List<T> batch;
        private int rowCount;

        private BatchReadListener(ExcelReadOptions options, Consumer<List<T>> batchConsumer) {
            this.options = options;
            this.batchConsumer = batchConsumer;
            this.batch = new ArrayList<>(options.batchSize());
        }

        @Override
        public void invoke(T row, AnalysisContext context) {
            rowCount++;
            if (rowCount > options.maxRows()) {
                throw new ExcelProcessingException("Excel row limit exceeded: " + options.maxRows());
            }
            batch.add(row);
            if (batch.size() >= options.batchSize()) {
                flush();
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            flush();
        }

        @Override
        public void onException(Exception exception, AnalysisContext context) throws Exception {
            if (exception instanceof ExcelProcessingException processingException) {
                throw processingException;
            }
            if (exception instanceof ExcelDataConvertException conversionException) {
                int row = conversionException.getRowIndex() + 1;
                int column = conversionException.getColumnIndex() + 1;
                throw new ExcelProcessingException(
                        "Excel cell conversion failed at row " + row + ", column " + column, exception);
            }
            throw exception;
        }

        private void flush() {
            if (batch.isEmpty()) {
                return;
            }
            batchConsumer.accept(List.copyOf(batch));
            batch.clear();
        }
    }
}
