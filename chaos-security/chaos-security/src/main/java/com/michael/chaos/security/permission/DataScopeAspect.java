package com.michael.chaos.security.permission;

import com.michael.chaos.security.annotation.DataScope;
import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.datascope.DataScopeContext;
import com.michael.chaos.security.api.datascope.DataScopeRequest;
import com.michael.chaos.security.auth.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 基于 {@link DataScope} 注解的数据权限上下文切面。
 */
@Aspect
public class DataScopeAspect {

    /**
     * 执行业务方法期间设置数据权限策略编码。
     */
    @Around("@within(com.michael.chaos.security.annotation.DataScope) || @annotation(com.michael.chaos.security.annotation.DataScope)")
    public Object applyDataScope(ProceedingJoinPoint joinPoint) throws Throwable {
        DataScope dataScope = resolveDataScope(joinPoint);
        if (dataScope == null) {
            return joinPoint.proceed();
        }
        boolean pushed = DataScopeContext.push(DataScopeRequest.of(dataScope.value(), currentSubject()));
        try {
            return joinPoint.proceed();
        } finally {
            if (pushed) {
                DataScopeContext.pop();
            }
        }
    }

    /**
     * 方法级注解优先于类级注解。
     */
    private DataScope resolveDataScope(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        DataScope methodScope = AnnotatedElementUtils.findMergedAnnotation(signature.getMethod(), DataScope.class);
        if (methodScope != null) {
            return methodScope;
        }
        return AnnotatedElementUtils.findMergedAnnotation(signature.getDeclaringType(), DataScope.class);
    }

    private AccessSubject currentSubject() {
        return SecurityUtils.currentUser()
                .map(AccessSubject::from)
                .orElse(AccessSubject.ANONYMOUS);
    }
}
