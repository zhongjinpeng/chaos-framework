package com.chaos.excel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个 Java 字段与 Excel 列的映射关系。
 *
 * <p>该注解同时服务于导入、导出和模板生成：导入按 {@link #name()} 校验表头并按
 * {@link #index()} 读取单元格；导出按列顺序写出表头与值；模板按 {@link #width()}、
 * {@link #required()}、{@link #format()} 和 {@link #dropdown()} 生成列定义。</p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {

    /**
     * 列索引，从 0 开始；默认按字段声明顺序自动编号。
     *
     * @return 列索引，负数表示按声明顺序自动分配
     */
    int index() default -1;

    /**
     * Excel 表头名称，导入时用于表头校验。
     *
     * @return 非空表头名称
     */
    String name();

    /**
     * 是否为必填列；模板会给表头加必填标记，导入时为空会直接被拒绝。
     *
     * @return 必填返回 {@code true}
     */
    boolean required() default false;

    /**
     * 模板列宽，单位为 Excel 字符宽度；0 表示使用默认宽度。
     *
     * @return 非负列宽
     */
    int width() default 0;

    /**
     * 数字或日期的显示格式，例如 {@code yyyy-MM-dd}、{@code #,##0.00}。
     *
     * @return Excel 显示格式，空字符串表示使用默认格式
     */
    String format() default "";

    /**
     * 模板下拉定义的 key，对应 {@code TemplateSheet.dropdown(key, options)}。
     *
     * @return 下拉定义 key，空字符串表示没有下拉
     */
    String dropdown() default "";

    /**
     * 模板填写提示。
     *
     * @return 提示文案，空字符串表示没有提示
     */
    String hint() default "";

    /**
     * 自定义单元格转换器；默认使用按字段类型自动选择的转换器。
     *
     * @return 转换器类型
     */
    Class<? extends com.chaos.excel.mapping.CellConverter<?>> converter()
            default com.chaos.excel.mapping.AutoCellConverter.class;
}
