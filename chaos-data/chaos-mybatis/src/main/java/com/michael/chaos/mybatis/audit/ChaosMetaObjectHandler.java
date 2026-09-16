package com.michael.chaos.mybatis.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import java.time.LocalDateTime;
import java.util.Objects;
import org.apache.ibatis.reflection.MetaObject;

/**
 * MyBatis Plus 审计字段自动填充处理器。
 *
 * <p>时间字段和逻辑删除标记总是填充；操作人来自 {@link AuditorProvider}，取不到时保持为空。</p>
 */
public class ChaosMetaObjectHandler implements MetaObjectHandler {

    private final AuditorProvider auditorProvider;

    /**
     * 使用默认操作人提供者（读取 {@link com.michael.chaos.core.context.RequestContext}）。
     */
    public ChaosMetaObjectHandler() {
        this(defaultAuditorProvider());
    }

    /**
     * 使用指定操作人提供者。
     */
    public ChaosMetaObjectHandler(AuditorProvider auditorProvider) {
        this.auditorProvider = Objects.requireNonNull(auditorProvider, "auditorProvider must not be null");
    }

    /**
     * 默认操作人提供者。
     *
     * <p>不再按 classpath 探测 chaos-security：登录用户来源通过 {@link LoginUserAuditorProvider} 由自动装配显式组合。</p>
     */
    public static AuditorProvider defaultAuditorProvider() {
        return new RequestContextAuditorProvider();
    }

    /**
     * 新增数据时填充创建人、创建时间、更新人、更新时间和逻辑删除标记。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String auditor = auditor();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", String.class, auditor);
        strictInsertFill(metaObject, "updatedBy", String.class, auditor);
        fillLogicNotDeleted(metaObject);
    }

    /**
     * 更新数据时填充更新人和更新时间。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updatedBy", String.class, auditor());
    }

    /**
     * 填充逻辑删除字段的“未删除”值。
     *
     * <p>{@code @TableLogic} 字段没有 {@code fill} 属性，{@code strictInsertFill} 对它不生效；值为 null 时 MyBatis Plus
     * 插入会跳过该列，表没有默认值就会写入 NULL，之后 {@code deleted = 0} 的查询查不到这行数据。</p>
     */
    private void fillLogicNotDeleted(MetaObject metaObject) {
        TableInfo tableInfo = findTableInfo(metaObject);
        if (tableInfo == null || !tableInfo.isWithLogicDelete()) {
            return;
        }
        TableFieldInfo field = tableInfo.getLogicDeleteFieldInfo();
        if (!metaObject.hasSetter(field.getProperty()) || metaObject.getValue(field.getProperty()) != null) {
            return;
        }
        Object value = convert(field.getLogicNotDeleteValue(), field.getPropertyType());
        if (value != null) {
            metaObject.setValue(field.getProperty(), value);
        }
    }

    private static Object convert(String value, Class<?> type) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (type == Integer.class || type == int.class) {
            return Integer.valueOf(value);
        }
        if (type == Long.class || type == long.class) {
            return Long.valueOf(value);
        }
        if (type == Boolean.class || type == boolean.class) {
            return "1".equals(value) || Boolean.parseBoolean(value);
        }
        if (type == String.class) {
            return value;
        }
        return null;
    }

    private String auditor() {
        String auditor = auditorProvider.currentAuditor();
        return auditor == null || auditor.isBlank() ? null : auditor;
    }
}
