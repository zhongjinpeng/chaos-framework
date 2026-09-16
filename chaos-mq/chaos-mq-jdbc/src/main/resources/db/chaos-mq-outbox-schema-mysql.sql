-- chaos-mq outbox 表（MySQL 8.0+ / MariaDB 10.5+）。
-- MySQL 不支持 create index if not exists，索引直接写在建表语句中。
-- 时间字段使用 datetime(3) 保留毫秒，与 claim 超时比较精度一致。
create table if not exists chaos_mq_outbox (
    id bigint not null auto_increment primary key,
    message_id varchar(128) not null,
    topic varchar(255) not null,
    tag varchar(128) null,
    payload_json longtext not null,
    headers_json text not null,
    status varchar(32) not null,
    retry_times int not null default 0,
    next_retry_at datetime(3) null,
    claimed_at datetime(3) null,
    claim_owner varchar(128) null,
    last_error varchar(1024) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_chaos_mq_outbox_message_id (message_id),
    key idx_chaos_mq_outbox_due (status, next_retry_at, created_at),
    key idx_chaos_mq_outbox_claimed (status, claimed_at),
    key idx_chaos_mq_outbox_updated (status, updated_at)
) engine = InnoDB default charset = utf8mb4;
