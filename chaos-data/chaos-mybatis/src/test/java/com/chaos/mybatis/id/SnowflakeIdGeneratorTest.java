package com.chaos.mybatis.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {
    @Test
    void bothEntryPointsAndConcurrentCallsShareOneSequence() {
        var generator = new SnowflakeIdGenerator(7, 3);
        var ids = ConcurrentHashMap.<Long>newKeySet();
        IntStream.range(0, 20000).parallel().forEach(i -> {
            long id = i % 2 == 0 ? generator.nextId() : generator.nextId(new Object());
            assertThat(ids.add(id)).isTrue();
            assertThat((id >> 12) & 31).isEqualTo(7);
            assertThat((id >> 17) & 31).isEqualTo(3);
        });
        assertThat(ids).hasSize(20000);
        var other = new SnowflakeIdGenerator(8, 3);
        assertThat(ids).doesNotContain(other.nextId());
    }

    @Test
    void invalidNodesAreRejectedAndCustomStrategyIsUsedDirectly() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 0)).isInstanceOf(com.baomidou.mybatisplus.core.exceptions.MybatisPlusException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0, -1)).isInstanceOf(com.baomidou.mybatisplus.core.exceptions.MybatisPlusException.class);
        assertThat(new MybatisIdentifierGeneratorAdapter(() -> 9007199254740993L).nextId(null))
                .isEqualTo(9007199254740993L);
    }
}
