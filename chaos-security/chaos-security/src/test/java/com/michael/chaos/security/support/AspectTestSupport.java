package com.michael.chaos.security.support;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * 切面测试辅助：用动态代理伪造 {@link ProceedingJoinPoint}。
 *
 * <p>切面只用到连接点的少数几个方法（方法签名、参数、目标对象、proceed），为此启动一个 Spring 容器
 * 或引入 mock 框架都太重；这里只实现切面真正会调用的方法，其余返回类型默认值。</p>
 */
public final class AspectTestSupport {

    /**
     * {@code proceed()} 的固定返回值，便于断言"放行时返回了业务结果"。
     */
    public static final String PROCEED_RESULT = "ok";

    private AspectTestSupport() {
    }

    /**
     * 创建指向给定方法的连接点。
     *
     * @param method 被拦截的方法
     * @param args 方法参数，切面读取资源属性时使用
     */
    public static ProceedingJoinPoint joinPoint(Method method, Object... args) {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                MethodSignature.class.getClassLoader(),
                new Class<?>[]{MethodSignature.class},
                (proxy, invokedMethod, invokedArgs) -> switch (invokedMethod.getName()) {
                    case "getMethod" -> method;
                    case "getDeclaringType" -> method.getDeclaringClass();
                    case "getName" -> method.getName();
                    default -> defaultValue(invokedMethod.getReturnType());
                });
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, invokedMethod, invokedArgs) -> switch (invokedMethod.getName()) {
                    case "getSignature" -> signature;
                    case "getArgs" -> args;
                    case "getTarget" -> null;
                    case "proceed" -> PROCEED_RESULT;
                    default -> defaultValue(invokedMethod.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type.isPrimitive() && type != void.class) {
            return 0;
        }
        return null;
    }
}
