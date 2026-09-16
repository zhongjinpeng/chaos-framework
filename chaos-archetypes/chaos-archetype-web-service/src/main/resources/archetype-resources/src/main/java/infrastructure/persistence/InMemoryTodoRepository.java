package ${package}.infrastructure.persistence;

import ${package}.domain.todo.Todo;
import ${package}.domain.todo.TodoRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 内存版待办仓储，让生成的项目无需数据库即可启动。
 *
 * <p>数据按租户分桶保存，演示多租户隔离。接入数据库时：追加 chaos-mybatis-starter，新增 Mapper 与持久化实体，
 * 用新的 {@code TodoRepository} 实现替换本类（chaos-mybatis 会自动为 SQL 追加租户条件）。</p>
 */
@Repository
public class InMemoryTodoRepository implements TodoRepository {

    private final Map<String, Map<String, Todo>> todosByTenant = new ConcurrentHashMap<>();

    @Override
    public void save(Todo todo) {
        todosByTenant.computeIfAbsent(todo.tenantId(), tenant -> new ConcurrentHashMap<>()).put(todo.id(), todo);
    }

    @Override
    public Optional<Todo> findById(String tenantId, String id) {
        return Optional.ofNullable(todosByTenant.getOrDefault(tenantId, Map.of()).get(id));
    }

    @Override
    public List<Todo> findAll(String tenantId) {
        return todosByTenant.getOrDefault(tenantId, Map.of()).values().stream()
                .sorted(Comparator.comparing(Todo::title))
                .toList();
    }
}
