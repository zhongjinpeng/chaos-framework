package com.chaos.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证报表服务事务副作用组件在提交、回滚和无事务三种场景下的执行边界。
 */
class TransactionalSideEffectsTests {

  private final TransactionalSideEffects sideEffects = new TransactionalSideEffects();

  @Test
  void afterCommitShouldRunImmediatelyWithoutTransaction() {
    AtomicBoolean committed = new AtomicBoolean();

    sideEffects.afterCommit(() -> committed.set(true));

    assertThat(committed).isTrue();
  }

  @Test
  void onRollbackShouldBeIgnoredWithoutTransaction() {
    AtomicBoolean rolledBack = new AtomicBoolean();

    sideEffects.onRollback(() -> rolledBack.set(true));

    assertThat(rolledBack).isFalse();
  }

  @Test
  void afterCommitShouldRunOnlyAfterCommitAndSkipOnRollback() throws Exception {
    AtomicBoolean committed = new AtomicBoolean();
    AtomicBoolean rolledBack = new AtomicBoolean();
    TransactionTemplate transaction = transaction();

    transaction.executeWithoutResult(
        status -> {
          sideEffects.afterCommit(() -> committed.set(true));
          sideEffects.onRollback(() -> rolledBack.set(true));
          assertThat(committed).isFalse();
        });

    assertThat(committed).isTrue();
    assertThat(rolledBack).isFalse();

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      sideEffects.afterCommit(() -> committed.set(true));
                      sideEffects.onRollback(() -> rolledBack.set(true));
                      throw new IllegalStateException("rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(committed).isTrue();
    assertThat(rolledBack).isTrue();
  }

  @Test
  void sideEffectFailureShouldBeLoggedInsteadOfPropagated() {
    assertThatCode(
            () ->
                sideEffects.afterCommit(
                    () -> {
                      throw new IllegalStateException("cleanup failed");
                    }))
        .doesNotThrowAnyException();
  }

  @Test
  void propagatingActionShouldReportFailureToCaller() throws Exception {
    assertThatThrownBy(
            () ->
                sideEffects.afterCommitPropagating(
                    () -> {
                      throw new IllegalStateException("sync failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("sync failed");

    assertThatThrownBy(
            () ->
                transaction()
                    .executeWithoutResult(
                        status ->
                            sideEffects.afterCommitPropagating(
                                () -> {
                                  throw new IllegalStateException("after commit failed");
                                })))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("after commit failed");
  }

  @Test
  void hasActiveTransactionShouldFollowSpringSynchronization() throws Exception {
    assertThat(sideEffects.hasActiveTransaction()).isFalse();
    transaction()
        .executeWithoutResult(status -> assertThat(sideEffects.hasActiveTransaction()).isTrue());
    assertThat(sideEffects.hasActiveTransaction()).isFalse();
  }

  private static TransactionTemplate transaction() throws Exception {
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:report_side_effects_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    return new TransactionTemplate(new DataSourceTransactionManager(source));
  }
}
