package com.chaos.service.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 基于应用统一 {@link ObjectMapper} 的 JSON 编解码工具。
 *
 * <p>该类型由框架自动装配为单例 Bean，保留宿主应用注册的 Jackson 模块、命名策略和日期格式。
 */
public final class JsonUtils {

    private final ObjectMapper objectMapper;

    /**
     * 创建 JSON 工具。
     *
     * @param objectMapper 应用统一配置的 Jackson 映射器
     */
    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 将对象序列化为 JSON 文本。
     *
     * @param value 待序列化对象，可为 {@code null}
     * @return JSON 文本；空值返回 {@code null} 字面量
     * @throws JsonOperationException 对象无法序列化时抛出
     */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonOperationException("JSON serialization failed", ex);
        }
    }

    /**
     * 将对象序列化为 UTF-8 JSON 字节。
     *
     * @param value 待序列化对象，可为 {@code null}
     * @return UTF-8 JSON 字节
     * @throws JsonOperationException 对象无法序列化时抛出
     */
    public byte[] toJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new JsonOperationException("JSON serialization failed", ex);
        }
    }

    /**
     * 将 JSON 文本反序列化为指定类型。
     *
     * @param json JSON 文本
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 反序列化结果
     * @throws JsonOperationException JSON 非法或无法转换为目标类型时抛出
     */
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new JsonOperationException("JSON deserialization failed", ex);
        }
    }

    /**
     * 将 JSON 文本反序列化为带泛型的指定类型。
     *
     * @param json JSON 文本
     * @param type 目标泛型类型
     * @param <T> 目标类型参数
     * @return 反序列化结果
     * @throws JsonOperationException JSON 非法或无法转换为目标类型时抛出
     */
    public <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new JsonOperationException("JSON deserialization failed", ex);
        }
    }

    /**
     * 将 JSON 文本解析为树模型。
     *
     * @param json JSON 文本
     * @return JSON 树节点
     * @throws JsonOperationException JSON 文本非法时抛出
     */
    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new JsonOperationException("JSON parsing failed", ex);
        }
    }

    /**
     * 创建与当前工具兼容的空对象节点。
     *
     * @return 可写 JSON 对象节点
     */
    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }

    /**
     * 递归按对象字段名排序并序列化，数组元素顺序保持不变。
     *
     * @param source 待规范化的 JSON 节点
     * @return 字段顺序稳定的 JSON 文本
     * @throws NullPointerException 节点为空时抛出
     * @throws JsonOperationException 节点无法序列化时抛出
     */
    public String toCanonicalJson(JsonNode source) {
        return toJson(sort(Objects.requireNonNull(source, "source must not be null")));
    }

    private JsonNode sort(JsonNode source) {
        if (source.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            source.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> sorted.set(name, sort(value)));
            return sorted;
        }
        if (source.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
            source.forEach(value -> sorted.add(sort(value)));
            return sorted;
        }
        return source.deepCopy();
    }
}
