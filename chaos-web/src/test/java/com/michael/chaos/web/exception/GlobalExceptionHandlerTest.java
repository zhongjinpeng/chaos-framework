package com.michael.chaos.web.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.web.support.TestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 全局异常处理器测试。
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * ResponseStatusException 应沿用自身状态码，而不是被兜底成 500。
     */
    @Test
    void shouldKeepStatusOfResponseStatusException() throws Exception {
        mockMvc.perform(get("/status"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("410"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    /**
     * 5xx 业务异常不能把内部信息回显给调用方。
     */
    @Test
    void shouldHideInternalMessageForServerErrors() throws Exception {
        mockMvc.perform(get("/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("internal server error"))
                .andExpect(content().string(not(containsString("password"))));
    }

    /**
     * 405 状态码与错误码保持一致。
     */
    @Test
    void shouldUseConsistentMethodNotAllowedCode() throws Exception {
        mockMvc.perform(put("/limited"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("405"));
    }

    /**
     * 缺少必填请求头返回 400，文案来自框架资源包的根（英文）条目。
     */
    @Test
    void shouldMapMissingHeaderToBadRequest() throws Exception {
        mockMvc.perform(get("/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required request header: X-Required"));
    }

    /**
     * 框架内置提示按 {@code Accept-Language} 切换语言。
     *
     * <p>这一条是本次国际化改造的核心验收点：改造前无论客户端声明什么语言，拿到的都是同一段硬编码中文。</p>
     */
    @Test
    void shouldLocalizeBuiltInMessageByAcceptLanguage() throws Exception {
        mockMvc.perform(get("/header").header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("缺少必填请求头：X-Required"));

        mockMvc.perform(get("/header").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required request header: X-Required"));
    }

    /**
     * 错误码默认文案同样参与国际化，不再出现"错误码英文、参数提示中文"的混杂响应。
     */
    @Test
    void shouldLocalizeErrorCodeMessage() throws Exception {
        mockMvc.perform(get("/server-error").header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("服务内部错误"));
    }

    /**
     * 未知异常返回 500 且不泄露异常原文。
     */
    @Test
    void shouldReturnGenericInternalErrorForUnknownException() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("boom"))));
    }
}
