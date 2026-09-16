package com.michael.chaos.mybatis.tenant;

import com.baomidou.mybatisplus.annotation.DbType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MyBatis Plus 集成配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.mybatis")
public class ChaosMybatisProperties {

    /**
     * 分页插件数据库类型。
     */
    @NotNull(message = "chaos.mybatis.db-type must not be null")
    private DbType dbType = DbType.MYSQL;

    /**
     * 多租户配置。
     */
    @Valid
    @NotNull(message = "chaos.mybatis.tenant must not be null")
    private Tenant tenant = new Tenant();

    /**
     * 数据权限配置。
     */
    @Valid
    @NotNull(message = "chaos.mybatis.data-scope must not be null")
    private DataScope dataScope = new DataScope();

    /**
     * 主键 ID 生成器配置。
     */
    @Valid
    @NotNull(message = "chaos.mybatis.id-generator must not be null")
    private IdGenerator idGenerator = new IdGenerator();

    /**
     * 分页配置。
     */
    @Valid
    @NotNull(message = "chaos.mybatis.pagination must not be null")
    private Pagination pagination = new Pagination();

    /**
     * 乐观锁配置。
     */
    @Valid
    @NotNull(message = "chaos.mybatis.optimistic-lock must not be null")
    private OptimisticLock optimisticLock = new OptimisticLock();

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType == null ? DbType.MYSQL : dbType;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant == null ? new Tenant() : tenant;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    public void setDataScope(DataScope dataScope) {
        this.dataScope = dataScope == null ? new DataScope() : dataScope;
    }

    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator == null ? new IdGenerator() : idGenerator;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination == null ? new Pagination() : pagination;
    }

    public OptimisticLock getOptimisticLock() {
        return optimisticLock;
    }

    public void setOptimisticLock(OptimisticLock optimisticLock) {
        this.optimisticLock = optimisticLock == null ? new OptimisticLock() : optimisticLock;
    }

    /**
     * 多租户 SQL 改写配置。
     */
    public static class Tenant {

        /**
         * 是否启用多租户 SQL 改写。
         */
        private boolean enabled = true;

        /**
         * 租户字段名。
         */
        @NotBlank(message = "chaos.mybatis.tenant.column must not be blank")
        private String column = "tenant_id";

        /**
         * 忽略多租户改写的表名前缀。
         */
        private List<String> ignoreTablePrefixes = new ArrayList<>(List.of("sys_"));

        /**
         * 忽略多租户改写的精确表名。
         */
        private List<String> ignoreTables = new ArrayList<>();

        /**
         * 当前租户 ID 缺失时的处理策略。
         */
        @NotNull(message = "chaos.mybatis.tenant.missing-tenant-behavior must not be null")
        private MissingTenantBehavior missingTenantBehavior = MissingTenantBehavior.DENY;

        /**
         * 租户 ID 白名单正则。租户 ID 会拼入 SQL 字面量，不匹配时拒绝执行 SQL。
         * 默认值与 chaos-core RequestContextSnapshot 的校验规则保持一致。
         */
        @NotBlank(message = "chaos.mybatis.tenant.id-pattern must not be blank")
        private String idPattern = "[A-Za-z0-9_.:@-]{1,128}";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }

        public List<String> getIgnoreTablePrefixes() {
            return ignoreTablePrefixes;
        }

        public void setIgnoreTablePrefixes(List<String> ignoreTablePrefixes) {
            this.ignoreTablePrefixes = ignoreTablePrefixes == null ? new ArrayList<>() : ignoreTablePrefixes;
        }

        public List<String> getIgnoreTables() {
            return ignoreTables;
        }

        public void setIgnoreTables(List<String> ignoreTables) {
            this.ignoreTables = ignoreTables == null ? new ArrayList<>() : ignoreTables;
        }

        public MissingTenantBehavior getMissingTenantBehavior() {
            return missingTenantBehavior;
        }

        public void setMissingTenantBehavior(MissingTenantBehavior missingTenantBehavior) {
            this.missingTenantBehavior = missingTenantBehavior == null ? MissingTenantBehavior.DENY : missingTenantBehavior;
        }

        public String getIdPattern() {
            return idPattern;
        }

        public void setIdPattern(String idPattern) {
            this.idPattern = idPattern;
        }
    }

    /**
     * 数据权限配置。
     */
    public static class DataScope {

        /**
         * 是否启用数据权限扩展点。
         */
        private boolean enabled = true;

        /**
         * 忽略数据权限改写的表名前缀。
         */
        private List<String> ignoreTablePrefixes = new ArrayList<>(List.of("sys_"));

        /**
         * 忽略数据权限改写的精确表名。
         */
        private List<String> ignoreTables = new ArrayList<>();

        /**
         * 空数据权限条件处理策略。
         */
        @NotNull(message = "chaos.mybatis.data-scope.empty-condition-behavior must not be null")
        private EmptyConditionBehavior emptyConditionBehavior = EmptyConditionBehavior.DENY;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getIgnoreTablePrefixes() {
            return ignoreTablePrefixes;
        }

        public void setIgnoreTablePrefixes(List<String> ignoreTablePrefixes) {
            this.ignoreTablePrefixes = ignoreTablePrefixes == null ? new ArrayList<>() : ignoreTablePrefixes;
        }

        public List<String> getIgnoreTables() {
            return ignoreTables;
        }

        public void setIgnoreTables(List<String> ignoreTables) {
            this.ignoreTables = ignoreTables == null ? new ArrayList<>() : ignoreTables;
        }

        public EmptyConditionBehavior getEmptyConditionBehavior() {
            return emptyConditionBehavior;
        }

        public void setEmptyConditionBehavior(EmptyConditionBehavior emptyConditionBehavior) {
            this.emptyConditionBehavior = emptyConditionBehavior == null ? EmptyConditionBehavior.DENY : emptyConditionBehavior;
        }
    }

    /**
     * MyBatis Plus 雪花 ID 生成器配置。
     */
    public static class IdGenerator {

        /**
         * 工作机器 ID，取值范围 0-31；生产多节点建议显式指定且保持唯一组合。
         */
        @Min(value = 0, message = "chaos.mybatis.id-generator.worker-id must be between 0 and 31")
        @Max(value = 31, message = "chaos.mybatis.id-generator.worker-id must be between 0 and 31")
        private Long workerId;

        /**
         * 数据中心 ID，取值范围 0-31；生产多节点建议显式指定且保持唯一组合。
         */
        @Min(value = 0, message = "chaos.mybatis.id-generator.datacenter-id must be between 0 and 31")
        @Max(value = 31, message = "chaos.mybatis.id-generator.datacenter-id must be between 0 and 31")
        private Long datacenterId;

        public Long getWorkerId() {
            return workerId;
        }

        public void setWorkerId(Long workerId) {
            this.workerId = workerId;
        }

        public Long getDatacenterId() {
            return datacenterId;
        }

        public void setDatacenterId(Long datacenterId) {
            this.datacenterId = datacenterId;
        }
    }

    /**
     * 分页配置。
     */
    public static class Pagination {

        /**
         * 单页最大条数。请求超过该值时按该值查询，防止 {@code size=100000000} 触发全表查询或批量导出；
         * 设为 0 或负数表示不限制（不推荐）。
         */
        private long maxLimit = 500;

        /**
         * 页码超过总页数时是否回到第一页。
         */
        private boolean overflow;

        public long getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(long maxLimit) {
            this.maxLimit = maxLimit;
        }

        public boolean isOverflow() {
            return overflow;
        }

        public void setOverflow(boolean overflow) {
            this.overflow = overflow;
        }
    }

    /**
     * 乐观锁配置。
     */
    public static class OptimisticLock {

        /**
         * 是否注册乐观锁插件。只对带 {@code @Version} 字段的实体生效，可继承 {@code VersionedBaseEntity}。
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 数据权限条件为空时的处理策略。
     */
    public enum EmptyConditionBehavior {
        /**
         * 忽略数据权限条件，保持原 SQL。
         */
        IGNORE,
        /**
         * 追加永假条件，避免误放行。
         */
        DENY
    }

    /**
     * 租户 ID 缺失时的处理策略。
     */
    public enum MissingTenantBehavior {
        /**
         * 忽略租户条件，保持原 SQL。
         */
        IGNORE,
        /**
         * 直接拒绝 SQL 改写，避免误放行。
         */
        DENY
    }
}
