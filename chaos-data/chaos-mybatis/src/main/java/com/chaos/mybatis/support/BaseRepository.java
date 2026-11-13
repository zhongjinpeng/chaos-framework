package com.chaos.mybatis.support;

import com.chaos.core.id.IdGenerator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/**
 * 仓储实现共用的主键准备、写入结果检查、异常转换和顺序分批能力。
 *
 * <p>不持有 Mapper、领域模型或租户上下文，不决定聚合保存顺序、事务、重试或版本回填。
 * Spring 注入统一 IdGenerator；手动创建仓储的测试仅在需要发号时提供测试生成器。
 */
public abstract class BaseRepository {
    private IdGenerator idGenerator;

    /**
     * 注入应用的唯一发号策略，不能为 null；不持有静态全局状态。
     * @param idGenerator 与 MyBatis-Plus ASSIGN_ID 共用的生成器
     */
    @Autowired
    public final void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    /**
     * 为本次写入预先分配主键；事务回滚允许断号。
     * @return 新的 64 位主键
     */
    protected final long nextId() {
        return Objects.requireNonNull(idGenerator, "IdGenerator has not been injected").nextId();
    }

    /**
     * 为批量 SQL 参数逐行分配独立主键，不修改原始行或输入列表。
     * @param values 非 null 的原始行列表；空列表不发号
     * @param <T> 原始行类型
     * @return 与输入顺序和数量一致的不可修改列表，保留重复行
     */
    protected final <T> List<IdentifiedRow<T>> assignIds(List<T> values) {
        Objects.requireNonNull(values, "values must not be null");
        return values.stream().map(value -> new IdentifiedRow<>(nextId(), value)).toList();
    }

    /**
     * 检查必须恰好写入一行的操作；不推断失败原因，也不修改版本。
     * @param affectedRows 单次 Mapper 写入返回的影响行数；不接收 JDBC 批处理状态码
     * @param failure 行数不等于 1 时才创建的业务异常
     */
    protected static void requireOneRow(int affectedRows, Supplier<? extends RuntimeException> failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        if (affectedRows != 1) {
            throw failure.get();
        }
    }

    /**
     * 检查以零行表示未命中的写入，保留调用方原有的零行失败语义。
     * @param affectedRows 单次 Mapper 写入返回的影响行数；不接收 JDBC 批处理状态码
     * @param failure 行数为 0 时才创建的业务异常
     */
    protected static void requireAffectedRow(int affectedRows, Supplier<? extends RuntimeException> failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        if (affectedRows == 0) {
            throw failure.get();
        }
    }

    /**
     * 执行一次操作，仅将唯一键冲突交给调用方转换，不重试、不吞掉其他异常。
     * @param operation 在当前调用线程及事务内执行的操作
     * @param translator 从原始唯一键异常生成业务异常，由调用方决定错误码及是否保留 cause
     * @param <T> 操作返回值类型
     * @return 操作的原始返回值，允许为 null
     */
    protected static <T> T translateDuplicateKey(
            Supplier<T> operation, Function<DuplicateKeyException, ? extends RuntimeException> translator) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(translator, "translator must not be null");
        try {
            return operation.get();
        } catch (DuplicateKeyException ex) {
            throw translator.apply(ex);
        }
    }

    /**
     * 为无返回值的操作转换唯一键冲突，不改变事务范围。
     * @param operation 在当前调用线程及事务内执行的操作
     * @param translator 接收原始唯一键异常的业务异常转换器
     */
    protected static void translateDuplicateKey(
            Runnable operation, Function<DuplicateKeyException, ? extends RuntimeException> translator) {
        Objects.requireNonNull(operation, "operation must not be null");
        translateDuplicateKey(() -> {
            operation.run();
            return null;
        }, translator);
    }

    /**
     * 按输入顺序分批执行，空输入不调用操作；任一批失败后立即停止并原样抛出异常。
     *
     * <p>不创建事务、不提交、不重试。批次为原列表的只读视图，执行期间调用方不得修改原列表。
     * @param values 非 null 的输入列表
     * @param batchSize 每批最大条数，必须大于 0
     * @param operation 消费当前批次的同步操作，负责选择真正的批量 SQL 或批量查询
     * @param <T> 输入元素类型
     */
    protected static <T> void forEachBatch(List<T> values, int batchSize, Consumer<List<T>> operation) {
        Objects.requireNonNull(values, "values must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        for (int from = 0; from < values.size();) {
            int to = from + Math.min(batchSize, values.size() - from);
            operation.accept(java.util.Collections.unmodifiableList(values.subList(from, to)));
            from = to;
        }
    }
}
