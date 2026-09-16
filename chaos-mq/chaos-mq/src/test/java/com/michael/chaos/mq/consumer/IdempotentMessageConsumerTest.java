package com.michael.chaos.mq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.idempotent.IdempotentRepository;
import com.michael.chaos.mq.MessageEnvelope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 消息幂等消费者测试。
 */
class IdempotentMessageConsumerTest {

    /**
     * 同一消息成功消费后再次投递应被跳过。
     */
    @Test
    void shouldSkipAlreadyCompletedMessage() {
        MapRepository repository = new MapRepository();
        List<String> consumed = new ArrayList<>();
        IdempotentMessageConsumer<String> consumer = new IdempotentMessageConsumer<>(
                message -> consumed.add(message.messageId()), repository);

        consumer.consume(envelope("msg-1"));
        consumer.consume(envelope("msg-1"));

        assertThat(consumed).containsExactly("msg-1");
        assertThat(repository.keys).containsKey("mq:order.created:msg-1:done")
                .doesNotContainKey("mq:order.created:msg-1:processing");
    }

    /**
     * 消费失败后应释放标记，重投时可以重新处理。
     */
    @Test
    void shouldAllowRetryAfterFailure() {
        MapRepository repository = new MapRepository();
        List<String> consumed = new ArrayList<>();
        boolean[] fail = {true};
        IdempotentMessageConsumer<String> consumer = new IdempotentMessageConsumer<>(message -> {
            consumed.add(message.messageId());
            if (fail[0]) {
                throw new IllegalStateException("db down");
            }
        }, repository);

        assertThatThrownBy(() -> consumer.consume(envelope("msg-2"))).isInstanceOf(IllegalStateException.class);
        fail[0] = false;
        consumer.consume(envelope("msg-2"));

        assertThat(consumed).containsExactly("msg-2", "msg-2");
        assertThat(repository.keys).containsKey("mq:order.created:msg-2:done");
    }

    /**
     * 并发重复投递时应要求 MQ 稍后重投，而不是直接 ack。
     */
    @Test
    void shouldAskRetryLaterWhenMessageIsInProcessing() {
        MapRepository repository = new MapRepository();
        repository.keys.put("mq:order.created:msg-3:processing", Duration.ofMinutes(5));
        IdempotentMessageConsumer<String> consumer = new IdempotentMessageConsumer<>(message -> {
        }, repository);

        assertThatThrownBy(() -> consumer.consume(envelope("msg-3")))
                .isInstanceOf(MessageRetryLaterException.class);
    }

    /**
     * 消费组应参与幂等 key，避免多个服务共用 Redis 时互相跳过。
     */
    @Test
    void shouldIsolateKeysByConsumerGroup() {
        MapRepository repository = new MapRepository();
        List<String> consumed = new ArrayList<>();
        new IdempotentMessageConsumer<String>(message -> consumed.add("a"), repository,
                Duration.ofDays(1), Duration.ofMinutes(1), "service-a").consume(envelope("msg-4"));
        new IdempotentMessageConsumer<String>(message -> consumed.add("b"), repository,
                Duration.ofDays(1), Duration.ofMinutes(1), "service-b").consume(envelope("msg-4"));

        assertThat(consumed).containsExactly("a", "b");
    }

    private static MessageEnvelope<String> envelope(String messageId) {
        return new MessageEnvelope<>(messageId, "order.created", "", "payload", Map.of(), null);
    }

    private static final class MapRepository implements IdempotentRepository {

        private final Map<String, Duration> keys = new HashMap<>();

        @Override
        public boolean saveIfAbsent(String key, Duration ttl) {
            return keys.putIfAbsent(key, ttl) == null;
        }

        @Override
        public void remove(String key) {
            keys.remove(key);
        }
    }
}
