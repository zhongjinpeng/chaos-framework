package ${package}.domain.todo;

import java.util.List;
import java.util.Optional;

/**
 * 待办仓储接口。
 *
 * <p>接口属于领域层，实现放在 infrastructure 层；替换为数据库实现时领域与应用层代码无需改动。</p>
 */
public interface TodoRepository {

    /**
     * 保存聚合（新增或更新）。
     */
    void save(Todo todo);

    /**
     * 按租户和 ID 查询。
     */
    Optional<Todo> findById(String tenantId, String id);

    /**
     * 查询租户下全部待办。
     */
    List<Todo> findAll(String tenantId);
}
