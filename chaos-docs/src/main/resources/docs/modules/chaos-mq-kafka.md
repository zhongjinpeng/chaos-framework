# chaos-mq-kafka

## 职责

Kafka 发布器适配模块，源码目录为 `chaos-mq/chaos-mq-kafka`，对外 artifactId 为 `chaos-mq-kafka`。

## 依赖方式

通过 `chaos-mq-starter` 引入，并在业务应用中添加 `spring-kafka`（适配器中为 optional）。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-mq-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

## 配置

连接信息使用 `spring.kafka.*`。存在 `KafkaTemplate` Bean 时自动注册 `KafkaMessagePublisher`。

```yaml
chaos:
  mq:
    publisher:
      send-timeout: 10s
```

## 行为

- **同步确认**：`publish` 等待 `KafkaTemplate#send` 返回的 future 完成。broker 拒绝、超时、序列化失败或线程中断都会抛出 `MessagePublishException`，outbox 派发器据此进入重试。旧版本丢弃 future，broker 不可用时消息仍被标记为 SENT。
- **消息头**：自动调用 `withTraceHeaders()` 补齐 trace 头；写入 `message-id`（与 RocketMQ 适配器一致）和 `message-tag`；record key 为 messageId。
- 建议生产配置 `spring.kafka.producer.acks=all` 与 `enable.idempotence=true`。

## 示例

```java
KafkaMessagePublisher publisher = new KafkaMessagePublisher(kafkaTemplate, Duration.ofSeconds(5));
publisher.publish(new MessageEnvelope<>(messageId, "order.created", "created", payload, Map.of(), Instant.now()));
```

真实 Kafka 集成测试：

```bash
./mvnw -pl chaos-mq/chaos-mq-kafka -am -Pchaos-integration-test verify
```

## 注意事项

- 同步等待会占用调用线程，业务请求内不要直接调用 `MessagePublisher`，应通过 outbox 异步发送。
- 集成测试验证 broker 内消息头包含 `X-Trace-Id`、`X-Span-Id`、`traceparent`、`tracestate` 和 `baggage`。
