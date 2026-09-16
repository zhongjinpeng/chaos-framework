package ${package}.domain.todo;

import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.domain.model.AggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 待办事项聚合根（示例）。
 *
 * <p>业务规则写在聚合内部：标题不能为空、已完成的待办不能重复完成。状态变化通过领域事件对外发布，
 * 应用服务在事务提交后读取 {@link #domainEvents()} 并交给事件发布器。</p>
 */
public class Todo extends AggregateRoot<String> {

    private final String id;

    private final String tenantId;

    private final String title;

    private boolean completed;

    private Todo(String id, String tenantId, String title, boolean completed) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.completed = completed;
    }

    /**
     * 创建待办。
     *
     * @param tenantId 所属租户，数据按租户隔离
     * @param title 标题
     */
    public static Todo create(String tenantId, String title) {
        if (title == null || title.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "待办标题不能为空");
        }
        Todo todo = new Todo(UUID.randomUUID().toString(), Objects.requireNonNull(tenantId), title.trim(), false);
        todo.registerEvent(new TodoCreatedEvent(UUID.randomUUID().toString(), todo.id, tenantId, Instant.now()));
        return todo;
    }

    /**
     * 标记为已完成。
     */
    public void complete() {
        if (completed) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "待办已完成");
        }
        completed = true;
    }

    @Override
    public String id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String title() {
        return title;
    }

    public boolean completed() {
        return completed;
    }
}
