package ${package}.interfaces.rest;

import ${package}.application.TodoApplicationService;
import ${package}.application.TodoApplicationService.TodoView;
import com.michael.chaos.web.idempotent.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办 HTTP 接口。
 *
 * <p>返回值会被 chaos-web 自动包装为统一响应 {@code {code, message, data, traceId}}，
 * 异常由全局异常处理器转换为对应错误码，这里只需返回业务对象。</p>
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoApplicationService todoApplicationService;

    public TodoController(TodoApplicationService todoApplicationService) {
        this.todoApplicationService = todoApplicationService;
    }

    @GetMapping
    public List<TodoView> list() {
        return todoApplicationService.list();
    }

    /**
     * 创建待办。{@code @Idempotent} 要求请求携带 {@code Idempotency-Key} 头，防止客户端重试造成重复创建。
     */
    @PostMapping
    @Idempotent
    public TodoView create(@Valid @RequestBody CreateTodoRequest request) {
        return todoApplicationService.create(request.title());
    }

    @PostMapping("/{id}/complete")
    public TodoView complete(@PathVariable String id) {
        return todoApplicationService.complete(id);
    }

    /**
     * 创建待办请求。
     *
     * @param title 标题
     */
    public record CreateTodoRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题最长 100 个字符") String title
    ) {
    }
}
