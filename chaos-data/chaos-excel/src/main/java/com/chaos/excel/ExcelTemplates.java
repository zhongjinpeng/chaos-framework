package com.chaos.excel;

import com.chaos.excel.internal.poi.PoiTemplateWriter;
import com.chaos.excel.template.TemplateOptions;
import com.chaos.excel.template.TemplateSheet;
import java.util.List;
import java.util.Objects;

/**
 * Excel 导入模板生成门面。
 *
 * <p>模板的列定义来自行模型上的 {@link com.chaos.excel.annotation.ExcelColumn}；
 * 示例数据、下拉和说明页通过 {@link TemplateSheet} 声明。</p>
 */
public final class ExcelTemplates {

    private ExcelTemplates() {
    }

    /**
     * 写出多 Sheet 模板。
     *
     * @param filename 下载文件名，可省略 {@code .xlsx} 后缀
     * @param sheets 模板 Sheet 定义
     * @param options 模板选项
     * @return 包含模板内容的内存 XLSX 文档
     */
    public static ExcelDocument write(
            String filename, List<TemplateSheet> sheets, TemplateOptions options) {
        Objects.requireNonNull(sheets, "sheets must not be null");
        Objects.requireNonNull(options, "options must not be null");
        return ExcelDocument.xlsx(filename, PoiTemplateWriter.write(sheets, options));
    }
}
