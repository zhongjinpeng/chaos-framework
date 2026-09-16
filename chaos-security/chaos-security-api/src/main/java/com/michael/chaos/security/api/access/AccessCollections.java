package com.michael.chaos.security.api.access;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class AccessCollections {

    private AccessCollections() {
    }

    static Set<String> copyStrings(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> copied = new LinkedHashSet<>();
        for (Object value : values) {
            String normalized = normalizeString(value);
            if (!normalized.isBlank()) {
                copied.add(normalized);
            }
        }
        if (copied.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(copied);
    }

    static Map<String, Object> copyAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = normalizeString(entry.getKey());
            if (!key.isBlank()) {
                copied.put(key, entry.getValue());
            }
        }
        if (copied.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(copied);
    }

    static String normalizeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
