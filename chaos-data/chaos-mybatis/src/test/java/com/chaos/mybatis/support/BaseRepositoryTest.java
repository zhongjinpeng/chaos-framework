package com.chaos.mybatis.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.chaos.core.id.IdGenerator;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class BaseRepositoryTest {
    @Test
    void springInjectsSharedGeneratorIntoInheritedSetter() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var sequence = new AtomicLong(9007199254740993L);
            context.registerBean(IdGenerator.class, () -> sequence::getAndIncrement);
            context.registerBean("first", ExampleRepository.class);
            context.registerBean("second", ExampleRepository.class);
            context.refresh();
            assertThat(context.getBean("first", ExampleRepository.class).allocate()).isEqualTo(9007199254740993L);
            assertThat(context.getBean("second", ExampleRepository.class).allocate()).isEqualTo(9007199254740994L);
        }
    }

    @Test
    void directConstructionRequiresExplicitTestGenerator() {
        var repository = new ExampleRepository();
        assertThatThrownBy(repository::allocate).hasMessageContaining("has not been injected");
        assertThatThrownBy(() -> repository.setIdGenerator(null)).isInstanceOf(NullPointerException.class);
        repository.setIdGenerator(() -> 42L);
        assertThat(repository.allocate()).isEqualTo(42L);
    }

    static class ExampleRepository extends BaseRepository {
        long allocate() { return nextId(); }
    }
}
