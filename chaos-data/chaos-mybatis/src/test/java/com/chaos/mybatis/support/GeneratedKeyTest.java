package com.chaos.mybatis.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

class GeneratedKeyTest {

    @Test
    void requiredValueShouldReturnPopulatedKey() {
        GeneratedKey<Long> key = new GeneratedKey<>();

        key.setValue(42L);

        assertThat(key.getValue()).isEqualTo(42L);
        assertThat(key.requiredValue()).isEqualTo(42L);
    }

    @Test
    void requiredValueShouldRejectMissingKey() {
        GeneratedKey<Long> key = new GeneratedKey<>();

        assertThat(key.getValue()).isNull();
        assertThatThrownBy(key::requiredValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database did not return a generated key");
    }

    @Test
    void mybatisShouldWriteNestedKeyValueProperty() {
        GeneratedKey<Long> key = new GeneratedKey<>();

        SystemMetaObject.forObject(Map.of("key", key)).setValue("key.value", 42L);

        assertThat(key.requiredValue()).isEqualTo(42L);
    }
}
