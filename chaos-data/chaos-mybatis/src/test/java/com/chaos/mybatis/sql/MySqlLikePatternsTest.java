package com.chaos.mybatis.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MySqlLikePatternsTest {

    @Test
    void shouldEscapeWildcardsAndBackslash() {
        assertThat(MySqlLikePatterns.escape("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }

    @Test
    void shouldPreserveNullAndPlainText() {
        assertThat(MySqlLikePatterns.escape(null)).isNull();
        assertThat(MySqlLikePatterns.escape("keyword")).isEqualTo("keyword");
    }
}
