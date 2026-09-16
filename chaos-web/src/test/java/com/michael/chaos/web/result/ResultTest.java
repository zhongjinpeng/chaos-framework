package com.michael.chaos.web.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.domain.dto.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 统一响应模型测试。
 */
class ResultTest {

    /**
     * 分页响应应保留标准分页字段，便于 Controller 返回分页查询结果。
     */
    @Test
    void shouldCreatePagedSuccessResult() {
        Result<PageResult<String>> result = Result.page(List.of("order-1", "order-2"), 8, 2, 2);

        assertThat(result.code()).isEqualTo("0");
        assertThat(result.message()).isEqualTo("success");
        assertThat(result.data().records()).containsExactly("order-1", "order-2");
        assertThat(result.data().total()).isEqualTo(8);
        assertThat(result.data().pageNo()).isEqualTo(2);
        assertThat(result.data().pageSize()).isEqualTo(2);
    }
}
