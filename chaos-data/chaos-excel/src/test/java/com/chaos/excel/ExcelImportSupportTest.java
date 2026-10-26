package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.core.importing.ImportResult;
import com.chaos.core.importing.ImportSource;
import com.chaos.excel.annotation.ExcelColumn;
import com.chaos.excel.support.ExcelImportSupport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 校验应用层导入计划构建与暂存文件解析。
 */
class ExcelImportSupportTest {

    @Test
    void singleSheetPlanShouldReadRowsAndExposeSheetNames() {
        ExcelDocument document =
                ExcelExports.write(
                        "支持测试",
                        SupportRow.class,
                        List.of(new SupportRow("A-1")),
                        new ExcelExports.WriteOptions("数据", true));

        try (ImportSource source =
                ExcelImportSupport.stage(
                        "支持测试.xlsx",
                        new ByteArrayInputStream(document.content()),
                        document.content().length)) {
            assertThat(ExcelImportSupport.sheetNames(source)).containsExactly("数据");
            List<SupportRow> rows = new ArrayList<>();
            ImportResult result =
                    ExcelImportSupport.execute(
                            source,
                            ExcelImportSupport.singleSheetPlan(
                                    "数据",
                                    SupportRow.class,
                                    100,
                                    10,
                                    (row, rowNumber) -> rows.add(row)));
            assertThat(result.isSuccess()).isTrue();
            assertThat(rows)
                    .singleElement()
                    .satisfies(row -> assertThat(row.code).isEqualTo("A-1"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 测试行模型。 */
    public static class SupportRow {
        @ExcelColumn(name = "编号", index = 0)
        private String code;

        public SupportRow() {}

        SupportRow(String code) {
            this.code = code;
        }
    }
}
