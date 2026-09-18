package com.michael.chaos.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.api.auth.LoginUser;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 授权表达式求值测试。
 */
class AccessExpressionEvaluatorTest {

    private final AccessExpressionEvaluator evaluator = new AccessExpressionEvaluator();

    /**
     * 应支持按参数名与位置引用方法参数。
     */
    @Test
    void shouldResolveMethodArguments() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("update", Order.class);
        Object[] args = {new Order("o-1", "1001")};

        assertThat(evaluator.evaluateAsString("#order.id", method, args, null, null)).isEqualTo("o-1");
        assertThat(evaluator.evaluateAsString("#p0.ownerId", method, args, null, null)).isEqualTo("1001");
    }

    /**
     * 应支持引用当前登录用户与字面量。
     */
    @Test
    void shouldResolveUserVariableAndLiteral() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("update", Order.class);
        LoginUser user = new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of());

        assertThat(evaluator.evaluateAsString("#user.userId", method, new Object[]{null}, null, user))
                .isEqualTo("1001");
        assertThat(evaluator.evaluateAsString("'fixed'", method, new Object[]{null}, null, null))
                .isEqualTo("fixed");
    }

    /**
     * 空表达式返回空值，不应报错。
     */
    @Test
    void shouldReturnEmptyForBlankExpression() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("update", Order.class);

        assertThat(evaluator.evaluate("", method, new Object[]{null}, null, null)).isNull();
        assertThat(evaluator.evaluate(null, method, new Object[]{null}, null, null)).isNull();
        assertThat(evaluator.evaluateAsString(" ", method, new Object[]{null}, null, null)).isEmpty();
    }

    /**
     * 相同表达式应命中解析缓存，返回同一个解析结果。
     */
    @Test
    void shouldCacheParsedExpression() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("update", Order.class);
        Object[] args = {new Order("o-1", "1001")};

        assertThat(evaluator.evaluateAsString("#order.id", method, args, null, null))
                .isEqualTo(evaluator.evaluateAsString("#order.id", method, args, null, null));
    }

    /**
     * 非法表达式应抛出带有位置与可用变量说明的诊断异常。
     */
    @Test
    void shouldFailWithDiagnosticForInvalidExpression() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("update", Order.class);

        assertThatThrownBy(() -> evaluator.evaluate("#order.", method, new Object[]{null}, null, null))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("OrderService#update")
                .hasMessageContaining("#order.");
    }

    /**
     * 求值期异常同样转换为诊断异常。
     */
    @Test
    void shouldFailWithDiagnosticWhenEvaluationThrows() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("update", Order.class);

        assertThatThrownBy(() -> evaluator.evaluate("#order.id", method, new Object[]{null}, null, null))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("表达式求值失败");
    }

    private record Order(String id, String ownerId) {
    }

    private static final class OrderService {

        void update(Order order) {
            // 仅用于反射取方法签名
        }
    }
}
