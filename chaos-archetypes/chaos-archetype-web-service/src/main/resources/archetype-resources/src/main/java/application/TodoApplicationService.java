package ${package}.application;

import ${package}.domain.todo.Todo;
import ${package}.domain.todo.TodoRepository;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.security.annotation.Permission;
import com.michael.chaos.service.event.DomainEventPublisher;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 待办应用服务：编排用例、声明权限、发布领域事件。
 *
 * <p>租户 ID 从 {@link RequestContext} 读取，由 chaos-security 在认证通过后根据登录用户写入，
 * 不接受客户端请求头传入，避免越权访问其他租户数据。</p>
 *
 * <p>接入数据库后，在写操作上加 {@code @Transactional}（或使用 chaos-service 的 {@code TransactionExecutor}），
 * {@link DomainEventPublisher} 会自动在事务提交后再发布事件。</p>
 */
@Service
public class TodoApplicationService {

    private final TodoRepository todoRepository;

    private final DomainEventPublisher domainEventPublisher;

    public TodoApplicationService(TodoRepository todoRepository, DomainEventPublisher domainEventPublisher) {
        this.todoRepository = todoRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 查询当前租户的待办。
     */
    @Permission("todo:read")
    public List<TodoView> list() {
        return todoRepository.findAll(currentTenant()).stream().map(TodoView::from).toList();
    }

    /**
     * 创建待办。
     */
    @Permission("todo:write")
    public TodoView create(String title) {
        Todo todo = Todo.create(currentTenant(), title);
        todoRepository.save(todo);
        domainEventPublisher.publishAll(todo.domainEvents());
        todo.clearDomainEvents();
        return TodoView.from(todo);
    }

    /**
     * 完成待办。
     */
    @Permission("todo:write")
    public TodoView complete(String id) {
        Todo todo = todoRepository.findById(currentTenant(), id)
                .orElseThrow(() -> new BizException(CommonErrorCode.NOT_FOUND, "待办不存在"));
        todo.complete();
        todoRepository.save(todo);
        return TodoView.from(todo);
    }

    private static String currentTenant() {
        String tenantId = RequestContext.tenantId();
        if (tenantId.isBlank()) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前登录用户未绑定租户");
        }
        return tenantId;
    }

    /**
     * 待办视图。
     *
     * @param id 待办 ID
     * @param title 标题
     * @param completed 是否完成
     */
    public record TodoView(String id, String title, boolean completed) {

        static TodoView from(Todo todo) {
            return new TodoView(todo.id(), todo.title(), todo.completed());
        }
    }
}
