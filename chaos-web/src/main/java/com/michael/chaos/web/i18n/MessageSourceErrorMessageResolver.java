package com.michael.chaos.web.i18n;

import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 基于 {@code MessageSource} 的错误消息解析器。
 *
 * <p>查找顺序：</p>
 * <ol>
 *     <li>应用自己的 {@code MessageSource}（默认即 {@code messages.properties}），业务可以覆盖任何框架文案；</li>
 *     <li>框架内置资源包 {@value #CHAOS_WEB_BUNDLE} 与 {@value #CHAOS_CORE_BUNDLE}；</li>
 *     <li>调用方给出的兜底文案。</li>
 * </ol>
 *
 * <p>之所以不把框架资源包直接挂到应用 {@code MessageSource} 的 parent 上：那个 Bean 由 Spring Boot 或业务自己创建，
 * 框架在自动装配里改它的父级属于越界修改，业务再设置一次 parent 就会把框架包顶掉。</p>
 *
 * <p>语言选择：配置了 {@code chaos.web.i18n.default-locale} 时固定使用该语言，否则使用当前请求语言
 * （Spring MVC 默认按 {@code Accept-Language} 解析，客户端未声明时取服务端默认语言）。
 * 框架资源包关闭了 {@code fallbackToSystemLocale}，未命中的语言会确定性地回退到英文根资源包，
 * 而不是随部署机器的 locale 漂移。</p>
 */
public class MessageSourceErrorMessageResolver implements ErrorMessageResolver {

    /**
     * Servlet Web 层文案资源包基名。
     */
    public static final String CHAOS_WEB_BUNDLE = "com.michael.chaos.web.i18n.messages.chaos-web";

    /**
     * 错误码文案资源包基名。
     *
     * <p>放在 chaos-core 而不是 chaos-web：错误码是 Servlet 服务与响应式网关共用的契约，
     * 网关不依赖 chaos-web，文案留在这边会让网关的错误响应永远只能输出英文兜底串。</p>
     */
    public static final String CHAOS_CORE_BUNDLE = "com.michael.chaos.core.i18n.messages.chaos-core";

    private final Supplier<MessageSource> applicationMessageSource;

    private final MessageSource chaosMessageSource;

    private final Locale fixedLocale;

    /**
     * 创建解析器。
     *
     * @param applicationMessageSource 应用 {@code MessageSource} 的惰性提供者，允许返回 {@code null}；
     *        使用 {@code Supplier} 而不是直接注入，是为了避免自动装配阶段提前初始化该 Bean
     * @param fixedLocale 固定语言；为 {@code null} 时按请求语言解析
     */
    public MessageSourceErrorMessageResolver(Supplier<MessageSource> applicationMessageSource, Locale fixedLocale) {
        this.applicationMessageSource = applicationMessageSource == null ? () -> null : applicationMessageSource;
        this.fixedLocale = fixedLocale;
        this.chaosMessageSource = createChaosMessageSource();
    }

    /**
     * 按消息 key 解析文案，未命中时返回兜底文案。
     */
    @Override
    public String resolve(String key, String defaultMessage, Object... args) {
        if (key == null || key.isBlank()) {
            return defaultMessage;
        }
        Locale locale = fixedLocale != null ? fixedLocale : LocaleContextHolder.getLocale();
        Object[] messageArgs = args == null || args.length == 0 ? null : args;
        String resolved = lookup(applicationMessageSource.get(), key, messageArgs, locale);
        if (resolved == null) {
            resolved = lookup(chaosMessageSource, key, messageArgs, locale);
        }
        return resolved != null ? resolved : defaultMessage;
    }

    /**
     * 单个 {@code MessageSource} 查找。
     *
     * <p>传入 {@code null} 作为 defaultMessage，未命中时返回 {@code null} 而不是抛
     * {@code NoSuchMessageException}，这样"查不到就换下一层"不需要靠捕获异常实现。</p>
     */
    private static String lookup(MessageSource messageSource, String key, Object[] args, Locale locale) {
        if (messageSource == null) {
            return null;
        }
        try {
            return messageSource.getMessage(key, args, null, locale);
        } catch (RuntimeException ex) {
            // 资源包损坏或消息格式非法不应让整个错误响应失败，降级到下一层查找。
            return null;
        }
    }

    private static MessageSource createChaosMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(CHAOS_WEB_BUNDLE, CHAOS_CORE_BUNDLE);
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
}
