-- chaos-framework MySQL 审计事件表。
-- 生产环境建议按 occurred_at 做分区或归档，避免审计表无限增长影响在线查询。

CREATE TABLE IF NOT EXISTS chaos_audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    action VARCHAR(128) NOT NULL COMMENT '审计动作编码',
    outcome VARCHAR(32) NOT NULL COMMENT '执行结果',
    principal_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '主体 ID',
    tenant_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '租户 ID',
    client_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '客户端 ID',
    trace_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '链路追踪 ID',
    ip VARCHAR(64) NOT NULL DEFAULT '' COMMENT '客户端 IP',
    uri VARCHAR(512) NOT NULL DEFAULT '' COMMENT '请求 URI',
    reason VARCHAR(512) NOT NULL DEFAULT '' COMMENT '失败或拒绝原因',
    occurred_at DATETIME(3) NOT NULL COMMENT '事件发生时间',
    attributes JSON NOT NULL COMMENT '已脱敏扩展属性',
    PRIMARY KEY (id),
    KEY idx_chaos_audit_event_occurred_at (occurred_at),
    KEY idx_chaos_audit_event_trace_id (trace_id),
    KEY idx_chaos_audit_event_principal_id (principal_id),
    KEY idx_chaos_audit_event_tenant_id (tenant_id),
    KEY idx_chaos_audit_event_action (action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'chaos 审计事件表';
