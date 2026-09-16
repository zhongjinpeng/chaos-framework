package com.michael.chaos.test.context;

import com.michael.chaos.tenant.TenantContext;
import com.michael.chaos.tenant.TenantDescriptor;

/**
 * 可选的 TenantContext 桥接。
 *
 * <p>chaos-tenant 是 optional 依赖：只用 chaos-core 的项目没有 {@code TenantContext}。
 * 真正引用租户类型的代码放在内部类 {@link TenantSupport} 中，只有检测到类存在时才会被加载，
 * 避免在缺少 chaos-tenant 的 classpath 上出现 {@code NoClassDefFoundError}。</p>
 */
final class TenantContextBridge {

    private static final boolean TENANT_PRESENT = isPresent("com.michael.chaos.tenant.TenantContext");

    private TenantContextBridge() {
    }

    static Object capture() {
        return TENANT_PRESENT ? TenantSupport.capture() : null;
    }

    static void activate(String tenantId) {
        if (TENANT_PRESENT) {
            TenantSupport.activate(tenantId);
        }
    }

    static void restore(Object previous) {
        if (TENANT_PRESENT) {
            TenantSupport.restore(previous);
        }
    }

    static void clear() {
        if (TENANT_PRESENT) {
            TenantSupport.clear();
        }
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, TenantContextBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    /**
     * 实际访问 TenantContext 的代码，只在 chaos-tenant 存在时加载。
     */
    private static final class TenantSupport {

        private TenantSupport() {
        }

        static Object capture() {
            return TenantContext.current().orElse(null);
        }

        static void activate(String tenantId) {
            if (tenantId == null || tenantId.isBlank()) {
                TenantContext.clear();
                return;
            }
            TenantContext.set(TenantDescriptor.active(tenantId));
        }

        static void restore(Object previous) {
            if (previous instanceof TenantDescriptor descriptor) {
                TenantContext.set(descriptor);
            } else {
                TenantContext.clear();
            }
        }

        static void clear() {
            TenantContext.clear();
        }
    }
}
