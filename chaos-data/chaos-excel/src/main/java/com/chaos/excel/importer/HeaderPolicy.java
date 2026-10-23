package com.chaos.excel.importer;

/** 表头校验策略。 */
public enum HeaderPolicy {
    /** 缺列、多列、顺序或名称不一致都拒绝。 */
    STRICT,
    /** 只校验期望列存在且名称一致，忽略多余列。 */
    LENIENT
}
