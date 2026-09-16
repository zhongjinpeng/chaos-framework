package com.michael.chaos.web.i18n;

import com.michael.chaos.core.exception.ErrorCode;

/**
 * 错误消息解析契约。
 *
 * <p>框架返回给调用方的文案有三个来源：错误码的默认消息、异常携带的自定义消息、以及框架内置的参数校验提示。
 * 在引入本接口之前，前两者是英文而第三类是硬编码中文，同一个服务的错误响应会中英混杂，
 * 且无论客户端是什么语言都只能拿到同一种文案。所有文案统一经过这里解析后，既保证语言一致，
 * 也让 {@code Accept-Language} 真正生效。</p>
 *
 * <p>业务系统可以注册自己的实现覆盖框架默认行为，例如把文案接到配置中心。</p>
 */
public interface ErrorMessageResolver {

    /**
     * 按消息 key 解析文案。
     *
     * @param key 消息 key
     * @param defaultMessage key 不存在时返回的兜底文案
     * @param args 消息参数，按 {@code {0}}、{@code {1}} 顺序替换
     * @return 解析后的文案；key 不存在时返回 {@code defaultMessage}
     */
    String resolve(String key, String defaultMessage, Object... args);

    /**
     * 解析错误码对应的展示文案。
     *
     * @param errorCode 错误码
     * @return 国际化文案，未配置时返回 {@link ErrorCode#message()}
     */
    default String resolve(ErrorCode errorCode) {
        String key = errorCode.messageKey();
        if (key == null || key.isBlank()) {
            return errorCode.message();
        }
        return resolve(key, errorCode.message());
    }

    /**
     * 解析异常携带的自定义消息。
     *
     * <p>业务抛出的 {@code BizException} 既可能携带最终文案（"订单已取消"），也可能携带一个消息 key
     * （"order.already-cancelled"）。这里统一先当作 key 查找，查不到就按字面量原样返回，
     * 因此老代码传字面量不受影响，新代码传 key 即可获得国际化能力。</p>
     *
     * @param errorCode 错误码，自定义消息为空时用它兜底
     * @param messageOrKey 异常携带的消息或消息 key
     * @return 解析后的文案
     */
    default String resolve(ErrorCode errorCode, String messageOrKey) {
        if (messageOrKey == null || messageOrKey.isBlank()) {
            return resolve(errorCode);
        }
        return resolve(messageOrKey, messageOrKey);
    }

    /**
     * 返回不做任何查找、直接使用兜底文案的解析器。
     *
     * <p>用于 {@code chaos.web.i18n.enabled=false} 和不需要国际化的单元测试。</p>
     */
    static ErrorMessageResolver none() {
        return (key, defaultMessage, args) -> defaultMessage;
    }
}
