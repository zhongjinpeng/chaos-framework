package com.michael.chaos.autoconfigure.audit.jdbc;

import com.michael.chaos.audit.DefaultAuditAttributeSanitizer;
import com.michael.chaos.audit.jdbc.JdbcAuditEventPublisher;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JDBC 审计持久化配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.audit.jdbc")
public class ChaosAuditJdbcProperties {

    /**
     * 是否启用 JDBC 审计持久化。
     */
    private boolean enabled = false;

    /**
     * 审计事件表名，支持 `table` 或 `schema.table` 格式。
     */
    @NotBlank(message = "chaos.audit.jdbc.table-name must not be blank")
    private String tableName = JdbcAuditEventPublisher.DEFAULT_TABLE_NAME;

    /**
     * 落库失败时是否直接抛出异常。
     */
    private boolean failFast = false;

    /**
     * 是否在 REQUIRES_NEW 独立事务中写入审计。
     *
     * <p>默认 {@code true}：避免业务回滚时审计丢失，以及 PostgreSQL 上审计 SQL 失败导致外层事务进入 aborted 状态。</p>
     */
    private boolean independentTransaction = true;

    /**
     * 敏感属性脱敏占位值。
     */
    @NotBlank(message = "chaos.audit.jdbc.mask-value must not be blank")
    private String maskValue = DefaultAuditAttributeSanitizer.DEFAULT_MASK_VALUE;

    /**
     * 按属性名包含匹配的敏感字段关键字（属性名去掉 {@code _ - .} 并转小写后匹配）。
     *
     * <p>{@code code}、{@code pin} 等短关键字按精确匹配内置处理，不要加入该列表，否则会误伤 {@code orderCode} 等字段。</p>
     */
    private List<String> sensitiveKeywords = new ArrayList<>(DefaultAuditAttributeSanitizer.DEFAULT_SENSITIVE_KEYWORDS);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isIndependentTransaction() {
        return independentTransaction;
    }

    public void setIndependentTransaction(boolean independentTransaction) {
        this.independentTransaction = independentTransaction;
    }

    public String getMaskValue() {
        return maskValue;
    }

    public void setMaskValue(String maskValue) {
        this.maskValue = maskValue;
    }

    public List<String> getSensitiveKeywords() {
        return sensitiveKeywords;
    }

    public void setSensitiveKeywords(List<String> sensitiveKeywords) {
        this.sensitiveKeywords = sensitiveKeywords == null ? new ArrayList<>() : sensitiveKeywords;
    }
}
