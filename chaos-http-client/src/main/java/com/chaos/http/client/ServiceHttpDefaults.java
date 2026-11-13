package com.chaos.http.client;

/**
 * 服务间 HTTP 调用的默认传输限制。
 */
public final class ServiceHttpDefaults {

    /** 连接超时毫秒数。 */
    public static final int CONNECT_TIMEOUT_MILLIS = 2500;

    /** 常规读取超时毫秒数。 */
    public static final int READ_TIMEOUT_MILLIS = 4000;

    /** 常规响应上限字节数。 */
    public static final int MAX_RESPONSE_BYTES = 1_048_576;

    private ServiceHttpDefaults() {
    }
}
