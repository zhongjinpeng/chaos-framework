package com.michael.chaos.security.api.access;

/**
 * 操作符对比较值的要求，供 {@link AccessPolicyFactory} 在启动期校验配置。
 *
 * @param required 是否必须提供比较值或右侧属性引用
 * @param referenceSupported 是否支持属性对属性比较
 * @param minValues 固定值最少个数
 * @param maxValues 固定值最多个数
 */
record ComparandSpec(boolean required, boolean referenceSupported, int minValues, int maxValues) {

    private static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * 不需要比较值，例如 EXISTS。
     */
    static ComparandSpec none() {
        return new ComparandSpec(false, false, 0, 0);
    }

    /**
     * 需要一个或多个固定值，不支持属性引用，例如 IN。
     */
    static ComparandSpec values() {
        return new ComparandSpec(true, false, 1, UNLIMITED);
    }

    /**
     * 需要一个或多个固定值，也支持属性引用，例如 EQ。
     */
    static ComparandSpec valuesOrReference() {
        return new ComparandSpec(true, true, 1, UNLIMITED);
    }

    /**
     * 只支持一个固定值或一个属性引用，例如 GT。
     */
    static ComparandSpec singleOrReference() {
        return new ComparandSpec(true, true, 1, 1);
    }

    /**
     * 必须是两个固定值，例如 BETWEEN。
     */
    static ComparandSpec pair() {
        return new ComparandSpec(true, false, 2, 2);
    }
}
