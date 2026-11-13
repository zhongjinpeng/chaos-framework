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

    @Test
    void batchIdsPreserveDuplicatesOrderAndInputWithoutAllocatingForEmptyInput() {
        var repository = new ExampleRepository();
        var sequence = new AtomicLong(9007199254740993L);
        repository.setIdGenerator(sequence::getAndIncrement);
        var values = new java.util.ArrayList<>(java.util.List.of("first", "first", "last"));
        var rows = repository.assignIds(values);
        assertThat(rows).extracting(IdentifiedRow::id)
                .containsExactly(9007199254740993L, 9007199254740994L, 9007199254740995L);
        assertThat(rows).extracting(IdentifiedRow::value).containsExactlyElementsOf(values);
        assertThat(values).containsExactly("first", "first", "last");
        assertThatThrownBy(() -> rows.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(repository.assignIds(java.util.List.of())).isEmpty();
        assertThat(sequence.get()).isEqualTo(9007199254740996L);
    }

    @Test
    void rowChecksKeepExactOneAndZeroOnlyContractsAndCreateFailuresLazily() {
        var failure = new IllegalStateException("conflict");
        java.util.function.Supplier<RuntimeException> mustNotRun = () -> {
            throw new AssertionError("successful writes must not construct an exception");
        };
        BaseRepository.requireOneRow(1, mustNotRun);
        BaseRepository.requireAffectedRow(1, mustNotRun);
        BaseRepository.requireAffectedRow(2, mustNotRun);
        assertThatThrownBy(() -> BaseRepository.requireAffectedRow(0, () -> failure)).isSameAs(failure);
        for (int rows : new int[] {0, 2, -1}) {
            assertThatThrownBy(() -> BaseRepository.requireOneRow(rows, () -> failure)).isSameAs(failure);
        }
    }

    @Test
    void duplicateTranslationRunsOnceAndPreservesOtherFailures() {
        var duplicate = new org.springframework.dao.DuplicateKeyException("unique index");
        var calls = new AtomicLong();
        java.util.function.Supplier<String> operation = () -> {
            calls.incrementAndGet();
            throw duplicate;
        };
        assertThatThrownBy(() -> BaseRepository.translateDuplicateKey(operation,
                cause -> new IllegalStateException("business conflict", cause)))
                .isInstanceOf(IllegalStateException.class).hasCause(duplicate);
        assertThat(calls.get()).isEqualTo(1);
        var other = new org.springframework.dao.DataAccessResourceFailureException("unavailable");
        java.util.function.Supplier<String> failing = () -> { throw other; };
        assertThatThrownBy(() -> BaseRepository.translateDuplicateKey(failing,
                cause -> new AssertionErrorException())).isSameAs(other);
        assertThat(BaseRepository.translateDuplicateKey(() -> "saved",
                cause -> new AssertionErrorException())).isEqualTo("saved");
        Runnable noResult = () -> { throw duplicate; };
        assertThatThrownBy(() -> BaseRepository.translateDuplicateKey(noResult,
                cause -> new IllegalStateException("void conflict", cause))).hasCause(duplicate);
    }

    @Test
    void batchesPreserveOrderBoundariesAndStopOnFirstFailure() {
        var batches = new java.util.ArrayList<java.util.List<Integer>>();
        var values = java.util.List.of(1, 2, 3, 4, 5);
        BaseRepository.forEachBatch(values, 2, batches::add);
        assertThat(batches).containsExactly(java.util.List.of(1, 2), java.util.List.of(3, 4), java.util.List.of(5));
        assertThatThrownBy(() -> batches.getFirst().clear()).isInstanceOf(UnsupportedOperationException.class);
        batches.clear();
        BaseRepository.forEachBatch(values, Integer.MAX_VALUE, batches::add);
        assertThat(batches).containsExactly(values);
        batches.clear();
        BaseRepository.forEachBatch(java.util.List.<Integer>of(), 2, batches::add);
        assertThat(batches).isEmpty();
        assertThatThrownBy(() -> BaseRepository.forEachBatch(values, 0, batches::add))
                .isInstanceOf(IllegalArgumentException.class);
        var failure = new IllegalStateException("batch failed");
        assertThatThrownBy(() -> BaseRepository.forEachBatch(values, 2, batch -> {
            batches.add(batch);
            if (batch.contains(3)) throw failure;
        })).isSameAs(failure);
        assertThat(batches).containsExactly(java.util.List.of(1, 2), java.util.List.of(3, 4));
        assertThat(values).containsExactly(1, 2, 3, 4, 5);
    }

    private static class AssertionErrorException extends RuntimeException {}

    static class ExampleRepository extends BaseRepository {
        long allocate() { return nextId(); }
    }
}
