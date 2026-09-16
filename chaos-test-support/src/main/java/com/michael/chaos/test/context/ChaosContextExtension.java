package com.michael.chaos.test.context;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * {@link WithChaosContext} 的 JUnit 5 扩展。
 *
 * <p>每个测试方法执行前按“方法注解优先、类注解兜底”写入上下文，执行后无论成功失败都关闭作用域，
 * 恢复进入前的 RequestContext / TenantContext。也可以不写注解，直接用
 * {@code @ExtendWith(ChaosContextExtension.class)} 获得“每个测试结束都清空上下文”的兜底隔离。</p>
 */
public class ChaosContextExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ChaosContextExtension.class);

    private static final String SCOPE_KEY = "scope";

    @Override
    public void beforeEach(ExtensionContext context) {
        Optional<WithChaosContext> annotation = findOnElement(context.getElement())
                .or(() -> context.getTestClass().flatMap(ChaosContextExtension::findOnClass));
        ChaosTestContext builder = annotation
                .map(value -> ChaosTestContext.empty()
                        .tenantId(value.tenantId())
                        .userId(value.userId())
                        .traceId(value.traceId())
                        .appName(value.appName()))
                .orElseGet(ChaosTestContext::empty);
        context.getStore(NAMESPACE).put(SCOPE_KEY, builder.open());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ChaosTestContext.Scope scope = context.getStore(NAMESPACE).remove(SCOPE_KEY, ChaosTestContext.Scope.class);
        if (scope != null) {
            scope.close();
        } else {
            ChaosTestContext.clearAll();
        }
    }

    private static Optional<WithChaosContext> findOnElement(Optional<? extends AnnotatedElement> element) {
        return element.flatMap(value -> AnnotationSupport.findAnnotation(value, WithChaosContext.class));
    }

    private static Optional<WithChaosContext> findOnClass(Class<?> testClass) {
        return AnnotationSupport.findAnnotation(testClass, WithChaosContext.class);
    }
}
