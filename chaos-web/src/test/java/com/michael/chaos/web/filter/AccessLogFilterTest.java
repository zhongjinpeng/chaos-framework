package com.michael.chaos.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.trace.RequestTimingContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccessLogFilterTest {

    /**
     * 耗时上下文要覆盖整个请求并在结束后清理，线程复用时不能把上一个请求的耗时算进来。
     */
    @Test
    void bindsTimingContextForTheWholeServletRequestAndCleansItAfterward() throws Exception {
        AccessLogFilter filter = new AccessLogFilter("test-service", "test");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            assertThat(RequestTimingContext.current()).isPresent();
            try (RequestTimingContext.StageScope ignored = RequestTimingContext.stage("business")) {
                currentResponse.getWriter().write("ok");
            }
        });

        assertThat(RequestTimingContext.current()).isEmpty();
    }

    /**
     * 健康检查每秒都在调，不能把访问日志刷爆。
     */
    @Test
    void excludesHealthEndpointWhenTimingLogIsEnabled() throws Exception {
        AccessLogFilter filter = new AccessLogFilter("test-service", "test");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        filter.doFilter(request, new MockHttpServletResponse(), (currentRequest, currentResponse) ->
                assertThat(RequestTimingContext.current()).isEmpty());
    }
}
