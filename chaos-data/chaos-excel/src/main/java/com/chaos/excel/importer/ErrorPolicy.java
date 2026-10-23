package com.chaos.excel.importer;

/**
 * 行级错误处理策略。
 */
public final class ErrorPolicy {

    private final boolean collect;
    private final int maxErrors;

    private ErrorPolicy(boolean collect, int maxErrors) {
        this.collect = collect;
        this.maxErrors = maxErrors;
    }

    /**
     * 遇到第一条错误立即中止。
     *
     * @return 快速失败策略
     */
    public static ErrorPolicy failFast() {
        return new ErrorPolicy(false, 1);
    }

    /**
     * 收集错误并继续处理后续行。
     *
     * @param maxErrors 最多保留的错误数
     * @return 收集策略
     */
    public static ErrorPolicy collect(int maxErrors) {
        if (maxErrors < 1) {
            throw new IllegalArgumentException("maxErrors must be greater than 0");
        }
        return new ErrorPolicy(true, maxErrors);
    }

    /** @return 是否收集错误并继续 */
    public boolean isCollect() {
        return collect;
    }

    /** @return 最多保留的错误数 */
    public int maxErrors() {
        return maxErrors;
    }
}
