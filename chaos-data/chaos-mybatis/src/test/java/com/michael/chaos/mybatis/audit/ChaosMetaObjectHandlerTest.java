package com.michael.chaos.mybatis.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.michael.chaos.mybatis.entity.BaseEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 审计字段填充测试。
 */
class ChaosMetaObjectHandlerTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SampleEntity.class);
    }

    /**
     * 不依赖 security 时也应填充时间和逻辑删除标记，操作人来自提供者。
     */
    @Test
    void shouldFillTimestampsAndDeletedWithoutSecurity() {
        SampleEntity entity = new SampleEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        new ChaosMetaObjectHandler(() -> "10001").insertFill(metaObject);

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getDeleted()).isZero();
        assertThat(entity.getCreatedBy()).isEqualTo("10001");
    }

    /**
     * 取不到操作人时字段保持为空，而不是写入空字符串。
     */
    @Test
    void shouldLeaveAuditorEmptyWhenUnknown() {
        SampleEntity entity = new SampleEntity();

        new ChaosMetaObjectHandler(() -> "").insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedBy()).isNull();
        assertThat(entity.getDeleted()).isZero();
    }

    /**
     * 测试实体。
     */
    public static class SampleEntity extends BaseEntity {

        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
