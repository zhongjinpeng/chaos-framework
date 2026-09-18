package com.michael.chaos.security.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * JWT claim 解析测试。
 */
class ChaosJwtClaimsTest {

    /**
     * 集合形态的 claim 应原样转换，并过滤空值。
     */
    @Test
    void shouldConvertCollectionClaim() {
        assertThat(ChaosJwtClaims.asStringSet(List.of("order:read", " order:write ", "")))
                .containsExactlyInAnyOrder("order:read", "order:write");
        assertThat(ChaosJwtClaims.asStringSet(Arrays.asList("order:read", null)))
                .containsExactly("order:read");
    }

    /**
     * 逗号分隔的字符串 claim 应拆分为集合。
     */
    @Test
    void shouldSplitCommaSeparatedClaim() {
        assertThat(ChaosJwtClaims.asStringSet("order:read, order:write"))
                .containsExactlyInAnyOrder("order:read", "order:write");
    }

    /**
     * 缺失或空 claim 返回空集合。
     */
    @Test
    void shouldReturnEmptyForBlankClaim() {
        assertThat(ChaosJwtClaims.asStringSet(null)).isEmpty();
        assertThat(ChaosJwtClaims.asStringSet("  ")).isEmpty();
        assertThat(ChaosJwtClaims.asStringSet(42)).isEmpty();
    }
}
