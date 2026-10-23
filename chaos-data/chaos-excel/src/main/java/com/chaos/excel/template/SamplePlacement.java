package com.chaos.excel.template;

/** 模板示例数据的放置方式。 */
public enum SamplePlacement {
    /** 示例数据写独立的 {@code 示例-*} Sheet，数据页保持干净。 */
    SEPARATE_SHEET,
    /** 示例数据写在数据页表头之后，导入时必须显式跳过。 */
    INLINE
}
