package com.chaos.core.id;

/**
 * 应用侧 64 位主键生成契约；不承诺连续、无间隙或跨节点严格时间排序。
 * 实现必须线程安全，生成失败应抛出异常，不得退回随机数或数据库自增。
 */
@FunctionalInterface
public interface IdGenerator {
    /**
     * 分配一个正的、有符号 64 位主键。调用方负责持久化，失败后允许断号。
     * @return 本生成器部署范围内唯一的主键
     */
    long nextId();
}
