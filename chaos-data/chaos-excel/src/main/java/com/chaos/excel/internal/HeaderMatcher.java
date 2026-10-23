package com.chaos.excel.internal;

import com.chaos.excel.importer.HeaderMismatchException;
import com.chaos.excel.importer.HeaderPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表头匹配器。
 */
public final class HeaderMatcher {

    private HeaderMatcher() {
    }

    /**
     * 校验实际表头与期望表头。
     *
     * @param actual 实际表头，按列索引
     * @param expected 期望表头
     * @param policy 校验策略
     * @throws HeaderMismatchException 不匹配时抛出
     */
    public static void validate(
            Map<Integer, String> actual, List<String> expected, HeaderPolicy policy) {
        List<String> actualList = toList(actual);
        List<String> expectedList = expected.stream().map(HeaderMatcher::normalize).toList();
        if (policy == HeaderPolicy.STRICT && actualList.size() > expectedList.size()) {
            throw new HeaderMismatchException(expectedList, actualList);
        }
        for (int index = 0; index < expectedList.size(); index++) {
            String actualValue = index < actualList.size()
                    ? normalize(actualList.get(index)) : "";
            if (!expectedList.get(index).equals(actualValue)) {
                throw new HeaderMismatchException(expectedList, actualList);
            }
        }
    }

    private static List<String> toList(Map<Integer, String> actual) {
        int size = 0;
        for (Integer index : actual.keySet()) {
            size = Math.max(size, index + 1);
        }
        List<String> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(actual.getOrDefault(index, ""));
        }
        return values;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.endsWith("*")) {
            normalized = normalized.substring(0, normalized.length() - 1).strip();
        }
        return normalized;
    }
}
