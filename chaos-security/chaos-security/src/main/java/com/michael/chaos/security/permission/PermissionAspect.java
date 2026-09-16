package com.michael.chaos.security.permission;

import com.michael.chaos.security.annotation.Permission;
import com.michael.chaos.security.auth.SecurityUtils;
import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 基于 {@link Permission} 注解的方法权限切面。
 */
@Aspect
public class PermissionAspect {

    private final PermissionCheckService permissionCheckService;

    private final AuditEventPublisher auditEventPublisher;

    private final ChaosMetrics metrics;

    /**
     * 创建权限切面。
     */
    public PermissionAspect(PermissionCheckService permissionCheckService, AuditEventPublisher auditEventPublisher) {
        this.permissionCheckService = permissionCheckService;
        this.auditEventPublisher = auditEventPublisher;
        this.metrics = NoopChaosMetrics.instance();
    }

    public PermissionAspect(PermissionCheckService permissionCheckService) {
        this(permissionCheckService, null);
    }

    /**
     * 创建权限切面并上报治理指标。
     */
    public PermissionAspect(
            PermissionCheckService permissionCheckService,
            AuditEventPublisher auditEventPublisher,
            ChaosMetrics metrics) {
        this.permissionCheckService = permissionCheckService;
        this.auditEventPublisher = auditEventPublisher;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 执行业务方法前校验权限编码。
     */
    @Around("@within(com.michael.chaos.security.annotation.Permission) || @annotation(com.michael.chaos.security.annotation.Permission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Permission permission = resolvePermission(joinPoint);
        if (permission == null || permission.value().isBlank()) {
            return joinPoint.proceed();
        }
        if (!permissionCheckService.hasPermission(SecurityUtils.currentUser(), permission.value())) {
            metrics.increment(ChaosMeterNames.SECURITY_ACCESS_DENIED, ChaosMeterNames.TAG_SOURCE, "permission");
            if (auditEventPublisher != null) {
                auditEventPublisher.publish(AuditSupport.event(
                                AuditAction.SECURITY_PERMISSION_DENIED,
                                AuditOutcome.DENIED)
                        .principalId(SecurityUtils.currentUser().map(user -> user.userId()).orElse(""))
                        .tenantId(SecurityUtils.currentUser().map(user -> user.tenantId()).orElse(""))
                        .reason("permission denied")
                        .attributes(AuditSupport.attributes("permission", permission.value()))
                        .build());
            }
            throw new PermissionDeniedException();
        }
        return joinPoint.proceed();
    }

    /**
     * 方法级注解优先于类级注解。
     */
    private Permission resolvePermission(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Permission methodPermission = AnnotatedElementUtils.findMergedAnnotation(signature.getMethod(), Permission.class);
        if (methodPermission != null) {
            return methodPermission;
        }
        return AnnotatedElementUtils.findMergedAnnotation(signature.getDeclaringType(), Permission.class);
    }
}
