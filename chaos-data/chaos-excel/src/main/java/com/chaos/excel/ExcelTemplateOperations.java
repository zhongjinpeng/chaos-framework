package com.chaos.excel;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.metadata.WriteSheet;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Facade for generating styled, validated multi-sheet XLSX import templates. */
public final class ExcelTemplateOperations {
    private ExcelTemplateOperations() {
    }

    /** A row model, sample data and the common workbook definition for one sheet. */
    public record Sheet(String sheetName, Class<?> rowType, Collection<?> sampleData,
                        ExcelTemplateSupport.SheetDefinition definition) {
        public Sheet {
            if (sheetName == null || sheetName.isBlank()) {
                throw new IllegalArgumentException("sheetName must not be blank");
            }
            Objects.requireNonNull(rowType, "rowType must not be null");
            sampleData = sampleData == null ? List.of() : List.copyOf(sampleData);
            Objects.requireNonNull(definition, "definition must not be null");
            if (!sheetName.equals(definition.sheetName())) {
                throw new IllegalArgumentException("sheetName must match definition.sheetName");
            }
        }
    }

    /** Write a multi-sheet template, then apply shared style and dropdown strategies. */
    public static ExcelDocument write(String filename, List<Sheet> sheets) {
        Objects.requireNonNull(sheets, "sheets must not be null");
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("sheets must not be empty");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = FastExcel.write(output).autoCloseStream(false).build()) {
            for (int index = 0; index < sheets.size(); index++) {
                Sheet definition = sheets.get(index);
                WriteSheet writeSheet = FastExcel.writerSheet(index, definition.sheetName())
                        .head(definition.rowType()).build();
                writer.write(definition.sampleData(), writeSheet);
            }
        } catch (RuntimeException exception) {
            throw new ExcelProcessingException("Excel template export failed", exception);
        }
        byte[] enhanced = ExcelTemplateSupport.enhance(
                output.toByteArray(), sheets.stream().map(Sheet::definition).toList());
        return ExcelDocument.xlsx(filename, enhanced);
    }
}
