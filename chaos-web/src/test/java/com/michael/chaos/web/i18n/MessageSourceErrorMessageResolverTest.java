package com.michael.chaos.web.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.core.exception.ErrorCode;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

/**
 * 错误文案解析器测试。
 */
class MessageSourceErrorMessageResolverTest {

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static ErrorMessageResolver resolver() {
        return new MessageSourceErrorMessageResolver(() -> null, null);
    }

    /**
     * 未配置应用 MessageSource 时也能读到框架内置资源包。
     */
    @Test
    void shouldResolveFromChaosBundle() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(resolver().resolve(CommonErrorCode.TOO_MANY_REQUESTS)).isEqualTo("too many requests");
    }

    /**
     * 按当前请求语言切换文案。
     */
    @Test
    void shouldResolveByCurrentLocale() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        assertThat(resolver().resolve(CommonErrorCode.TOO_MANY_REQUESTS)).isEqualTo("请求过于频繁，请稍后重试");
    }

    /**
     * 未提供对应语言资源包时确定性回退到英文根资源包，而不是跟着部署机器的默认语言漂移。
     */
    @Test
    void shouldFallBackToRootBundleForUnknownLocale() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertThat(resolver().resolve(CommonErrorCode.NOT_FOUND)).isEqualTo("not found");
    }

    /**
     * 固定语言优先于请求语言。
     */
    @Test
    void shouldPreferFixedLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        ErrorMessageResolver fixed = new MessageSourceErrorMessageResolver(() -> null, Locale.SIMPLIFIED_CHINESE);

        assertThat(fixed.resolve(CommonErrorCode.NOT_FOUND)).isEqualTo("请求的资源不存在");
    }

    /**
     * 应用自己的 MessageSource 优先，业务可以覆盖任何框架文案。
     */
    @Test
    void shouldLetApplicationMessageSourceOverrideFrameworkText() {
        StaticMessageSource applicationMessages = new StaticMessageSource();
        applicationMessages.addMessage(CommonErrorCode.NOT_FOUND.messageKey(), Locale.ENGLISH, "resource missing");
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ErrorMessageResolver custom = new MessageSourceErrorMessageResolver(() -> applicationMessages, null);

        assertThat(custom.resolve(CommonErrorCode.NOT_FOUND)).isEqualTo("resource missing");
    }

    /**
     * 带参数的文案按 MessageFormat 占位符替换。
     */
    @Test
    void shouldFormatArguments() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(resolver().resolve(ChaosMessageKeys.MISSING_PARAMETER, "fallback", "orderId"))
                .isEqualTo("Missing required parameter: orderId");
    }

    /**
     * 业务错误码默认落在 {@code chaos.error.<code>} 命名空间，且不会被内置错误码文案顶掉。
     */
    @Test
    void shouldKeepBusinessErrorCodeNamespaceSeparate() {
        ErrorCode businessNotFound = new ErrorCode() {
            @Override
            public String code() {
                return "404";
            }

            @Override
            public String message() {
                return "订单不存在";
            }
        };
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(businessNotFound.messageKey()).isEqualTo("chaos.error.404");
        assertThat(CommonErrorCode.NOT_FOUND.messageKey()).isEqualTo("chaos.error.common.not-found");
        assertThat(resolver().resolve(businessNotFound)).isEqualTo("订单不存在");
    }

    /**
     * 异常携带的自定义消息先当作 key 查找，查不到按字面量返回，老代码不受影响。
     */
    @Test
    void shouldTreatExplicitMessageAsKeyThenLiteral() {
        StaticMessageSource applicationMessages = new StaticMessageSource();
        applicationMessages.addMessage("order.already-cancelled", Locale.ENGLISH, "order already cancelled");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        ErrorMessageResolver custom = new MessageSourceErrorMessageResolver(() -> applicationMessages, null);

        assertThat(custom.resolve(CommonErrorCode.BAD_REQUEST, "order.already-cancelled"))
                .isEqualTo("order already cancelled");
        assertThat(custom.resolve(CommonErrorCode.BAD_REQUEST, "订单已取消")).isEqualTo("订单已取消");
        assertThat(custom.resolve(CommonErrorCode.BAD_REQUEST, null)).isEqualTo("bad request");
    }

    /**
     * 关闭国际化后直接返回兜底文案，不做任何查找。
     */
    @Test
    void shouldReturnDefaultWhenDisabled() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        assertThat(ErrorMessageResolver.none().resolve(CommonErrorCode.NOT_FOUND)).isEqualTo("not found");
    }
}
