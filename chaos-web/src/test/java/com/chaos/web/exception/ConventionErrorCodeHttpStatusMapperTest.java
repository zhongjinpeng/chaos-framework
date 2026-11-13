package com.chaos.web.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 约定错误码映射测试。
 */
class ConventionErrorCodeHttpStatusMapperTest {

    private final ConventionErrorCodeHttpStatusMapper mapper =
            new ConventionErrorCodeHttpStatusMapper(code -> code.startsWith("SVC_"));

    /**
     * 已知后缀映射为对应状态，未知业务码按 400 处理。
     */
    @Test
    void shouldMapKnownSuffixes() {
        assertEquals(HttpStatus.NOT_FOUND, mapper.resolve("SVC_ORDER_NOT_FOUND"));
        assertEquals(HttpStatus.FORBIDDEN, mapper.resolve("SVC_ORDER_FORBIDDEN"));
        assertEquals(HttpStatus.CONFLICT, mapper.resolve("SVC_ORDER_DUPLICATE"));
        assertEquals(HttpStatus.CONFLICT, mapper.resolve("SVC_ORDER_VERSION_CONFLICT"));
        assertEquals(HttpStatus.BAD_REQUEST, mapper.resolve("SVC_ORDER_INVALID"));
    }

    /**
     * 其他服务的错误码不参与映射。
     */
    @Test
    void shouldIgnoreForeignCodes() {
        assertNull(mapper.resolve("OTHER_NOT_FOUND"));
    }
}
