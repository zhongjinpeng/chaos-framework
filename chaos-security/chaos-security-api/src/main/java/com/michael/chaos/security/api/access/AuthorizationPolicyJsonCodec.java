package com.michael.chaos.security.api.access;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 策略定义的 JSON 编解码，供数据库、Redis、配置中心等远端策略来源复用。
 *
 * <p>枚举取值与 YAML 配置保持一致的宽松写法：{@code NOT_EQ}、{@code not-eq}、{@code notEq} 都能解析，
 * 使用方在 Redis 里手写策略时不必纠结大小写与分隔符。</p>
 *
 * <p>需要 classpath 上存在 jackson-databind（Spring Boot 应用默认都有）。</p>
 */
public class AuthorizationPolicyJsonCodec {

    private final ObjectMapper objectMapper;

    private final CollectionType listType;

    /**
     * 使用内置的宽松 ObjectMapper 创建编解码器。
     */
    public AuthorizationPolicyJsonCodec() {
        this(defaultObjectMapper());
    }

    /**
     * 使用自定义 ObjectMapper 创建编解码器；调用方需要自行注册枚举宽松解析。
     */
    public AuthorizationPolicyJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.listType = objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyDocument.class);
    }

    /**
     * 解析策略定义列表，支持 JSON 数组与单个 JSON 对象。
     *
     * @param json JSON 文本
     * @param source 来源描述，出错时写进诊断信息，例如 {@code Redis key chaos:security:access:policies}
     * @throws ChaosDiagnosticException JSON 非法时抛出
     */
    public List<PolicyDefinition> decode(String json, String source) {
        String text = json == null ? "" : json.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        try {
            if (text.startsWith("{")) {
                return List.of(objectMapper.readValue(text, PolicyDocument.class).toDefinition());
            }
            List<PolicyDocument> documents = objectMapper.readValue(text, listType);
            return documents.stream().map(PolicyDocument::toDefinition).toList();
        } catch (IOException ex) {
            throw new ChaosDiagnosticException(new ChaosDiagnostic(
                    "授权策略 JSON 无法解析：" + source,
                    List.of(rootMessage(ex)),
                    List.of(
                            "确认内容是策略对象数组，例如 [{\"id\":\"deny-external\",\"effect\":\"DENY\","
                                    + "\"actions\":[\"order:read\"],\"conditions\":[...]}]",
                            "字段名与 chaos.security.access.policies 配置一致：id / description / effect / actions / conditions / enabled",
                            "operator、effect 支持 NOT_EQ、not-eq、notEq 三种写法")), ex);
        }
    }

    /**
     * 序列化策略定义列表，便于把配置里的策略导出到远端来源。
     */
    public String encode(List<PolicyDefinition> definitions) {
        try {
            return objectMapper.writeValueAsString(definitions == null ? List.of() : definitions);
        } catch (IOException ex) {
            throw new ChaosDiagnosticException(ChaosDiagnostic.of(
                    "授权策略无法序列化为 JSON",
                    ex.getMessage(),
                    "检查策略定义中是否存在无法序列化的自定义对象"), ex);
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(AccessEffect.class, new RelaxedEnumDeserializer<>(AccessEffect.class));
        module.addDeserializer(AttributeOperator.class, new RelaxedEnumDeserializer<>(AttributeOperator.class));
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(module)
                .build();
    }

    /**
     * 宽松枚举解析：忽略大小写，把 {@code -} 和空格当作 {@code _}。
     */
    private static final class RelaxedEnumDeserializer<E extends Enum<E>> extends JsonDeserializer<E> {

        private final Class<E> enumType;

        private RelaxedEnumDeserializer(Class<E> enumType) {
            this.enumType = enumType;
        }

        @Override
        public E deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String raw = parser.getValueAsString();
            String normalized = normalize(raw);
            for (E candidate : enumType.getEnumConstants()) {
                if (candidate.name().equals(normalized)) {
                    return candidate;
                }
            }
            throw new IOException(enumType.getSimpleName() + " 不支持的取值：" + raw);
        }

        private String normalize(String raw) {
            if (raw == null) {
                return "";
            }
            String camelCaseToSnake = raw.trim().replaceAll("([a-z0-9])([A-Z])", "$1_$2");
            return camelCaseToSnake.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getName() : message;
    }

    /**
     * JSON 侧的策略文档。
     *
     * <p>不直接反序列化 {@link PolicyDefinition}：record 的 {@code boolean enabled} 在 JSON 缺省时会变成
     * {@code false}，写策略的人不写 enabled 就等于整条策略失效——这是个只在运行期才会发现的坑。
     * 这里用 {@code Boolean} 区分"没写"和"写了 false"，没写时按启用处理，与 YAML 配置的默认值一致。</p>
     */
    private static final class PolicyDocument {

        /** Jackson 直接读写字段，避免为一个内部 DTO 写一整套 getter/setter。 */
        public String id;

        public String description;

        public AccessEffect effect;

        public Set<String> actions;

        public List<ConditionDefinition> conditions;

        public Boolean enabled;

        private PolicyDefinition toDefinition() {
            return new PolicyDefinition(
                    id,
                    description,
                    effect,
                    actions,
                    conditions,
                    enabled == null || enabled);
        }
    }
}
