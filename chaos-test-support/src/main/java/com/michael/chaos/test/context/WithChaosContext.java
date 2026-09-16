package com.michael.chaos.test.context;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 为测试方法（或测试类中的每个方法）设置 chaos 请求上下文，方法结束后自动恢复。
 *
 * <pre>{@code
 * @WithChaosContext(tenantId = "tenant-a", userId = "1001")
 * class OrderServiceTest {
 *
 *     @Test
 *     @WithChaosContext(tenantId = "tenant-b")   // 方法级注解覆盖类级注解
 *     void shouldIsolateTenant() { ... }
 * }
 * }</pre>
 *
 * <p>上下文只写入执行测试方法的线程；测试内自行创建的线程池需要通过框架的上下文传播机制传递。</p>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(ChaosContextExtension.class)
public @interface WithChaosContext {

    /**
     * 租户 ID。
     */
    String tenantId() default "";

    /**
     * 用户 ID。
     */
    String userId() default "";

    /**
     * trace ID；为空时使用空字符串，由被测代码按需生成。
     */
    String traceId() default "";

    /**
     * 应用名。
     */
    String appName() default "test";
}
