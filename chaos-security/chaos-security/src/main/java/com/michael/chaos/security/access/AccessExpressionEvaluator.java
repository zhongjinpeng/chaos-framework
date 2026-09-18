package com.michael.chaos.security.access;

import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.api.auth.LoginUser;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * {@code @RequireAccess} 上的 SpEL 表达式求值器。
 *
 * <p>表达式可以引用方法参数（{@code #order}、{@code #p0}）、当前登录用户（{@code #user}）与目标 Bean
 * （{@code #root.target}）。表达式按文本缓存，避免每次调用重复解析。</p>
 */
public class AccessExpressionEvaluator {

    /**
     * 表达式缓存上限，超过后整体清空，避免动态拼接的表达式把内存撑爆。
     */
    private static final int EXPRESSION_CACHE_LIMIT = 1024;

    private final ExpressionParser parser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    /**
     * 求值并转换为字符串，表达式为空时返回空字符串。
     */
    public String evaluateAsString(String expression, Method method, Object[] args, Object target, LoginUser user) {
        Object value = evaluate(expression, method, args, target, user);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 求值表达式，表达式为空时返回 {@code null}。
     *
     * @throws ChaosDiagnosticException 表达式非法或求值失败时抛出
     */
    public Object evaluate(String expression, Method method, Object[] args, Object target, LoginUser user) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Expression parsed = parse(expression, method);
        try {
            return parsed.getValue(context(method, args, target, user));
        } catch (RuntimeException ex) {
            throw fail(expression, method, "表达式求值失败：" + ex.getMessage(), ex);
        }
    }

    private Expression parse(String expression, Method method) {
        Expression cached = expressions.get(expression);
        if (cached != null) {
            return cached;
        }
        try {
            Expression parsed = parser.parseExpression(expression);
            if (expressions.size() >= EXPRESSION_CACHE_LIMIT) {
                expressions.clear();
            }
            expressions.put(expression, parsed);
            return parsed;
        } catch (RuntimeException ex) {
            throw fail(expression, method, "表达式无法解析：" + ex.getMessage(), ex);
        }
    }

    private EvaluationContext context(Method method, Object[] args, Object target, LoginUser user) {
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                target,
                method,
                args == null ? new Object[0] : args,
                parameterNameDiscoverer);
        context.setVariable("user", user);
        return context;
    }

    private ChaosDiagnosticException fail(String expression, Method method, String cause, RuntimeException ex) {
        String location = method == null
                ? "@RequireAccess"
                : method.getDeclaringClass().getName() + "#" + method.getName() + " 上的 @RequireAccess";
        return new ChaosDiagnosticException(new ChaosDiagnostic(
                location + " 的表达式无法求值：" + expression,
                List.of(cause),
                List.of(
                        "表达式可用的变量：方法参数（#参数名 或 #p0）、当前登录用户 #user、目标 Bean #root.target",
                        "资源 ID 使用字面量时需要加引号，例如 resourceId = \"'fixed-id'\"",
                        "编译时需要保留参数名（chaos-boot-parent 已开启 -parameters），否则只能用 #p0 形式"
                )), ex);
    }
}
