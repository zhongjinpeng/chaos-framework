package com.michael.chaos.audit;

import java.util.Map;

/**
 * 审计扩展属性脱敏器。
 *
 * <p>审计事件允许携带少量扩展属性，但这些属性不能落入密码、验证码、token 等敏感原文。
 * 所有审计发布器（日志、JDBC 或自定义实现）在输出属性前都应调用该端口，
 * 业务系统可以替换为自己的合规脱敏策略。</p>
 */
@FunctionalInterface
public interface AuditAttributeSanitizer {

    /**
     * 对扩展属性执行脱敏。
     *
     * @param attributes 原始扩展属性
     * @return 已脱敏的扩展属性
     */
    Map<String, String> sanitize(Map<String, String> attributes);
}
