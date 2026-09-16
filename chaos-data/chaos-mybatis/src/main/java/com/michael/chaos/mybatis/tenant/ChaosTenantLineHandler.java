package com.michael.chaos.mybatis.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.mybatis.sql.SqlLiterals;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import net.sf.jsqlparser.expression.Expression;

/**
 * 多租户 SQL 改写处理器。
 *
 * <p>租户 ID 最终会拼进 SQL 字面量，这里做两层防护：</p>
 * <ol>
 *     <li>按 {@code chaos.mybatis.tenant.id-pattern} 白名单校验，不合法直接拒绝执行 SQL；</li>
 *     <li>通过 {@link SqlLiterals} 按方言转义，即使放宽白名单也不会产生注入。</li>
 * </ol>
 */
public class ChaosTenantLineHandler implements TenantLineHandler {

    private final TenantIdProvider tenantIdProvider;

    private final ChaosMybatisProperties properties;

    private final Pattern tenantIdPattern;

    /**
     * 创建多租户 SQL 改写处理器。
     */
    public ChaosTenantLineHandler(TenantIdProvider tenantIdProvider, ChaosMybatisProperties properties) {
        this.tenantIdProvider = Objects.requireNonNull(tenantIdProvider, "tenantIdProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.tenantIdPattern = Pattern.compile(properties.getTenant().getIdPattern());
    }

    /**
     * 返回当前租户 ID 字面量。
     *
     * @throws IllegalStateException 缺失租户且策略为 DENY，或租户 ID 不符合白名单
     */
    @Override
    public Expression getTenantId() {
        String tenantId = currentTenantId();
        if (tenantId.isEmpty()) {
            if (properties.getTenant().getMissingTenantBehavior() == ChaosMybatisProperties.MissingTenantBehavior.DENY) {
                throw new ChaosDiagnosticException(new ChaosDiagnostic(
                        "Missing tenant id for MyBatis tenant SQL rewrite：当前线程没有租户上下文，拒绝执行多租户 SQL",
                        List.of("chaos.mybatis.tenant.missing-tenant-behavior=DENY（默认）时，缺少租户的 SQL 不会被放行，避免全表读写",
                                "常见来源：请求未经过认证、定时任务/MQ 消费线程没有建立租户上下文、异步线程未传播上下文"),
                        List.of("Web 请求：确认 token 中带有租户 claim，或通过网关/可信代理传入租户",
                                "任务与消息：使用 DistributedJobRunner、ContextRestoringMessageConsumer，或用 RequestContext 显式设置租户",
                                "确实不需要租户隔离的表：加入 chaos.mybatis.tenant.ignore-tables 或 ignore-table-prefixes")));
            }
            return SqlLiterals.stringValue("", properties.getDbType());
        }
        if (!tenantIdPattern.matcher(tenantId).matches()) {
            // 不回显租户值本身：非法值可能就是注入载荷，写进日志同样有风险。
            throw new ChaosDiagnosticException(ChaosDiagnostic.of(
                    "Illegal tenant id for MyBatis tenant SQL rewrite：租户 ID 格式非法（长度 " + tenantId.length() + "），拒绝执行 SQL",
                    "租户 ID 不匹配 chaos.mybatis.tenant.id-pattern=" + tenantIdPattern.pattern(),
                    "检查租户来源（token claim、网关透传）是否被篡改；业务确实使用其他格式时调整 chaos.mybatis.tenant.id-pattern，"
                            + "并同步 chaos-core 的租户 ID 白名单"));
        }
        return SqlLiterals.stringValue(tenantId, properties.getDbType());
    }

    @Override
    public String getTenantIdColumn() {
        return properties.getTenant().getColumn();
    }

    /**
     * 忽略配置的表；租户缺失且策略为 IGNORE 时整体跳过租户条件。
     */
    @Override
    public boolean ignoreTable(String tableName) {
        ChaosMybatisProperties.Tenant tenant = properties.getTenant();
        return currentTenantId().isEmpty() && tenant.getMissingTenantBehavior() == ChaosMybatisProperties.MissingTenantBehavior.IGNORE
                || tenant.getIgnoreTables().contains(tableName)
                || tenant.getIgnoreTablePrefixes().stream().anyMatch(tableName::startsWith);
    }

    private String currentTenantId() {
        String tenantId = tenantIdProvider.currentTenantId();
        return tenantId == null ? "" : tenantId.trim();
    }
}
