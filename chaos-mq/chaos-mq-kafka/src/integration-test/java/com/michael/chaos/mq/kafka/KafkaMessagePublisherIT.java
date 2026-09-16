package com.michael.chaos.mq.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.trace.TraceContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kafka 真实基础设施集成测试。
 */
@Testcontainers
class KafkaMessagePublisherIT {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    /**
     * 每个用例结束后清理 trace 上下文。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * Kafka 发布器应把 W3C trace header 写入 broker 消息头。
     */
    @Test
    void shouldPublishTraceHeadersToKafka() {
        TraceContext.start("", "", TRACEPARENT, "vendor=state", "tenant=acme", "tenant-a", "user-1", "kafka-it");
        Map<String, Object> producerProperties = KafkaTestUtils.producerProps(KAFKA.getBootstrapServers());
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(kafkaTemplate);
        String topic = "chaos.trace.it";

        publisher.publish(new MessageEnvelope<>(
                "message-1",
                topic,
                "created",
                "payload",
                Map.of("business-key", "order-1"),
                Instant.now()
        ));
        kafkaTemplate.flush();

        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(),
                "chaos-trace-it",
                "true"
        );
        try (var consumer = new DefaultKafkaConsumerFactory<String, Object>(consumerProperties).createConsumer()) {
            consumer.subscribe(List.of(topic));

            ConsumerRecord<String, Object> record = KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo("message-1");
            assertThat(headerValue(record, ChaosHeaders.TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(headerValue(record, ChaosHeaders.TRACEPARENT))
                    .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-")
                    .endsWith("-01");
            assertThat(headerValue(record, ChaosHeaders.TRACESTATE)).isEqualTo("vendor=state");
            assertThat(headerValue(record, ChaosHeaders.BAGGAGE)).isEqualTo("tenant=acme");
            assertThat(headerValue(record, "business-key")).isEqualTo("order-1");
        }
    }

    private String headerValue(ConsumerRecord<String, Object> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
