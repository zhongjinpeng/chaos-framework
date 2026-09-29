
package com.chaos.autoconfigure.redis;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Applies the framework Redis serialization contract to ordinary Redis templates.
 *
 * <p>{@link StringRedisTemplate} already uses string serializers and is intentionally left
 * unchanged. Ordinary templates use readable UTF-8 keys while retaining JDK value serialization
 * because authorization-server values include immutable Spring Authorization Server types that
 * cannot be round-tripped by a generic JSON serializer.</p>
 */
public final class RedisSerializationSupport {

    private RedisSerializationSupport() {
    }

    /**
     * Creates the post processor used by Redis-related auto-configurations.
     *
     * @return serialization post processor
     */
    public static BeanPostProcessor postProcessor() {
        return new RedisTemplateSerializationPostProcessor();
    }

    private static final class RedisTemplateSerializationPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof RedisTemplate<?, ?> template)
                    || bean instanceof StringRedisTemplate) {
                return bean;
            }
            StringRedisSerializer keySerializer = new StringRedisSerializer(StandardCharsets.UTF_8);
            template.setKeySerializer(keySerializer);
            template.setHashKeySerializer(keySerializer);

            RedisSerializer<?> valueSerializer = template.getValueSerializer();
            if (valueSerializer == null) {
                JdkSerializationRedisSerializer jdkSerializer = new JdkSerializationRedisSerializer();
                template.setValueSerializer(jdkSerializer);
                template.setHashValueSerializer(jdkSerializer);
                template.setDefaultSerializer(jdkSerializer);
            } else if (template.getHashValueSerializer() == null) {
                template.setHashValueSerializer(valueSerializer);
            }
            return bean;
        }
    }
}
