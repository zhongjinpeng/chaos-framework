package com.michael.chaos.web.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.exception.CommonErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 框架文案资源包一致性测试。
 *
 * <p>资源包缺 key 不会报错，只会静默回退到另一种语言，线上表现为"大部分中文、个别英文"，很难被发现。
 * 这里在构建期把缺漏变成失败。</p>
 *
 * <p>覆盖两个资源包：错误码文案在 chaos-core（Servlet 服务与响应式网关共用），
 * Servlet Web 层专有提示在 chaos-web。</p>
 */
class ChaosMessageBundleTest {

    private static final String CORE_BUNDLE = "com/michael/chaos/core/i18n/messages/chaos-core";

    private static final String WEB_BUNDLE = "com/michael/chaos/web/i18n/messages/chaos-web";

    private static final List<String> BUNDLES = List.of(CORE_BUNDLE, WEB_BUNDLE);

    private static final List<String> LOCALIZED_SUFFIXES = List.of("_zh_CN", "_zh");

    private static Properties load(String bundle, String suffix) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ChaosMessageBundleTest.class.getClassLoader()
                .getResourceAsStream(bundle + suffix + ".properties")) {
            assertThat(in).as("bundle %s%s", bundle, suffix).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    /**
     * 各语言资源包的 key 集合必须与对应的根资源包完全一致。
     */
    @Test
    void shouldKeepSameKeysAcrossBundles() throws IOException {
        for (String bundle : BUNDLES) {
            Set<String> rootKeys = new LinkedHashSet<>(load(bundle, "").stringPropertyNames());
            assertThat(rootKeys).as("root keys of %s", bundle).isNotEmpty();
            for (String suffix : LOCALIZED_SUFFIXES) {
                assertThat(load(bundle, suffix).stringPropertyNames())
                        .as("bundle %s%s", bundle, suffix)
                        .containsExactlyInAnyOrderElementsOf(rootKeys);
            }
        }
    }

    /**
     * 每个内置错误码都要有文案，否则响应里会出现只有开发者看得懂的英文兜底串。
     */
    @Test
    void shouldCoverEveryCommonErrorCode() throws IOException {
        Set<String> coreKeys = load(CORE_BUNDLE, "").stringPropertyNames();

        assertThat(Arrays.stream(CommonErrorCode.values()).map(CommonErrorCode::messageKey).collect(Collectors.toSet()))
                .allMatch(coreKeys::contains);
    }

    /**
     * 错误码文案必须在 chaos-core 而不是 chaos-web。
     *
     * <p>chaos-gateway 是响应式栈、不依赖 chaos-web；文案留在 chaos-web 会让网关的错误响应
     * 永远只能输出英文兜底串，同一个调用方在"被网关拒绝"和"被服务拒绝"时看到不同语言。</p>
     */
    @Test
    void errorCodeMessagesShouldLiveInCoreBundle() throws IOException {
        assertThat(load(CORE_BUNDLE, "").stringPropertyNames()).allMatch(key -> key.startsWith("chaos.error."));
        assertThat(load(WEB_BUNDLE, "").stringPropertyNames()).noneMatch(key -> key.startsWith("chaos.error."));
    }

    /**
     * 所有 key 都能被 {@link ChaosMessageKeys} 或内置错误码引用到，避免资源包里堆积无人使用的条目。
     */
    @Test
    void shouldNotContainOrphanKeys() throws IOException {
        Set<String> referenced = new LinkedHashSet<>(List.of(
                ChaosMessageKeys.MISSING_PARAMETER,
                ChaosMessageKeys.MISSING_HEADER,
                ChaosMessageKeys.TYPE_MISMATCH,
                ChaosMessageKeys.MALFORMED_BODY,
                ChaosMessageKeys.METHOD_NOT_SUPPORTED,
                ChaosMessageKeys.MEDIA_TYPE_NOT_SUPPORTED,
                ChaosMessageKeys.MISSING_IDEMPOTENCY_KEY,
                ChaosMessageKeys.VALIDATION_FIELD));
        Arrays.stream(CommonErrorCode.values()).map(CommonErrorCode::messageKey).forEach(referenced::add);

        for (String bundle : BUNDLES) {
            assertThat(load(bundle, "").stringPropertyNames()).as(bundle).isSubsetOf(referenced);
        }
    }

    /**
     * 带占位符的文案必须是合法的 MessageFormat 模式，并且占位符个数与调用方传入的参数个数一致。
     *
     * <p>MessageFormat 里的单引号会吞掉后续占位符，这类错误只在运行时才暴露，必须在构建期拦下。</p>
     */
    @Test
    void shouldKeepPlaceholdersConsistent() throws IOException {
        for (String bundle : BUNDLES) {
            Properties root = load(bundle, "");
            for (String suffix : List.of("", "_zh_CN", "_zh")) {
                Properties localized = load(bundle, suffix);
                for (String key : localized.stringPropertyNames()) {
                    String pattern = localized.getProperty(key);
                    int expected = placeholderCount(root.getProperty(key));
                    assertThat(placeholderCount(pattern))
                            .as("placeholder count of %s in bundle '%s%s'", key, bundle, suffix)
                            .isEqualTo(expected);
                    Object[] args = new Object[expected];
                    Arrays.fill(args, "x");
                    assertThat(new MessageFormat(pattern, Locale.ROOT).format(args))
                            .as("formatted %s in bundle '%s%s'", key, bundle, suffix)
                            .doesNotContain("{0}");
                }
            }
        }
    }

    private static int placeholderCount(String pattern) {
        int count = 0;
        while (pattern.contains("{" + count + "}")) {
            count++;
        }
        return count;
    }
}
