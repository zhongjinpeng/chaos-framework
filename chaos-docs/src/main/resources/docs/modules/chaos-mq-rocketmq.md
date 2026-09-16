# chaos-mq-rocketmq

## 职责

RocketMQ 发布器适配模块，源码目录为 `chaos-mq/chaos-mq-rocketmq`，对外 artifactId 为 `chaos-mq-rocketmq`。

## 依赖方式

通过 `chaos-mq-starter` 引入，并在业务应用中添加 `rocketmq-spring-boot-starter`（适配器中为 optional）。

## 配置

连接信息使用 `rocketmq.*`。存在 `RocketMQTemplate` Bean 时自动注册 `RocketMqMessagePublisher`。

```yaml
chaos:
  mq:
    publisher:
      send-timeout: 10s
```

## 行为

- **同步确认**：使用 `syncSend(destination, message, timeout)`，只有 `SendStatus.SEND_OK` 视为成功。`FLUSH_DISK_TIMEOUT`、`FLUSH_SLAVE_TIMEOUT`、`SLAVE_NOT_AVAILABLE` 和发送异常都会抛出 `MessagePublishException`，由 outbox 重试（消费端幂等保证重复无害）。旧版本调用 `send` 不检查结果。
- **目标**：tag 非空时发送到 `topic:tag`。
- **消息头**：自动补齐 trace 头，写入 `message-id`。

## 示例

```java
RocketMqMessagePublisher publisher = new RocketMqMessagePublisher(rocketMQTemplate, Duration.ofSeconds(5));
publisher.publish(new MessageEnvelope<>(messageId, "order.created", "created", payload, Map.of(), Instant.now()));
```

## 注意事项

- RocketMQ 容器集成测试不放入默认 `chaos-integration-test` profile，需要单独管理 NameServer、Broker 和等待策略。
