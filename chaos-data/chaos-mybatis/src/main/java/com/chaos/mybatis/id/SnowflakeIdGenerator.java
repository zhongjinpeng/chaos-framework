package com.chaos.mybatis.id;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.chaos.core.id.IdGenerator;
import java.net.InetAddress;

/**
 * 复用 MyBatis-Plus 雪花算法；同一实例同时服务应用调用和 ASSIGN_ID。
 * 节点组合必须在所有并行写入实例间唯一，时钟回拨沿用 MyBatis-Plus 的等待/失败策略。
 */
public final class SnowflakeIdGenerator extends DefaultIdentifierGenerator implements IdGenerator {
    /** 仅用于单实例开发的自动节点推断。生产环境应显式指定节点。 */
    public SnowflakeIdGenerator() {
        super((InetAddress) null);
    }

    /**
     * @param workerId 0–31 的工作节点编号
     * @param datacenterId 0–31 的数据中心编号
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        super(workerId, datacenterId);
    }

    @Override
    public long nextId() {
        return super.nextId(null);
    }
}
