package com.michael.chaos.web.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.web.exception.GlobalExceptionHandler;
import com.michael.chaos.web.support.TestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 统一响应包装测试。
 */
class ResultResponseBodyAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice())
                .build();
    }

    /**
     * JSON 对象应被包装为 Result。
     */
    @Test
    void shouldWrapJsonBody() throws Exception {
        mockMvc.perform(get("/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.date").exists());
    }

    /**
     * ResponseEntity&lt;String&gt; 选中字符串转换器，不能被包装，否则会抛 ClassCastException。
     */
    @Test
    void shouldNotWrapStringResponseEntity() throws Exception {
        mockMvc.perform(get("/string-entity"))
                .andExpect(status().isOk())
                .andExpect(content().string("plain"));
    }

    /**
     * 声明返回 Object、运行时为字符串的响应同样不能被包装。
     */
    @Test
    void shouldNotWrapRuntimeStringBody() throws Exception {
        mockMvc.perform(get("/object-string"))
                .andExpect(status().isOk())
                .andExpect(content().string("plain-object"));
    }
}
