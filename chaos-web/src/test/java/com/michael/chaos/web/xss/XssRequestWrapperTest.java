package com.michael.chaos.web.xss;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * XSS 请求包装器测试。
 */
class XssRequestWrapperTest {

    /**
     * getParameterMap 是 Spring 数据绑定使用的入口，必须同样被转义。
     */
    @Test
    void shouldEscapeParameterMapAndHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name", "<script>");
        request.addHeader("X-Note", "<b>");
        request.addHeader("Authorization", "Bearer a<b");

        XssRequestWrapper wrapper = new XssRequestWrapper(request);

        assertThat(wrapper.getParameterMap().get("name")).containsExactly("&lt;script&gt;");
        assertThat(Collections.list(wrapper.getHeaders("X-Note"))).containsExactly("&lt;b&gt;");
        assertThat(wrapper.getHeader("Authorization")).isEqualTo("Bearer a<b");
    }
}
