package com.michael.chaos.web.xss;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class XssFilterTest {

    @Test
    void nonExcludedPath_wrapsRequestWithXssRequestWrapper() throws ServletException, IOException {
        XssFilter filter = new XssFilter(List.of("/api/richtext/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertInstanceOf(XssRequestWrapper.class, filterChain.getRequest());
    }

    @Test
    void excludedPath_passesOriginalRequest() throws ServletException, IOException {
        XssFilter filter = new XssFilter(List.of("/api/richtext/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/richtext/save");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertSame(request, filterChain.getRequest());
    }

    @Test
    void antPatternMatching_excludesMatchingPaths() throws ServletException, IOException {
        XssFilter filter = new XssFilter(List.of("/api/richtext/**", "/admin/content/*"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Deep nested path matches **
        MockHttpServletRequest deepPath = new MockHttpServletRequest("POST", "/api/richtext/article/123");
        MockFilterChain chain1 = new MockFilterChain();
        filter.doFilter(deepPath, response, chain1);
        assertSame(deepPath, chain1.getRequest());

        // Single segment matches *
        MockHttpServletRequest singleSegment = new MockHttpServletRequest("POST", "/admin/content/edit");
        MockFilterChain chain2 = new MockFilterChain();
        filter.doFilter(singleSegment, response, chain2);
        assertSame(singleSegment, chain2.getRequest());

        // Non-matching path is still wrapped
        MockHttpServletRequest nonMatching = new MockHttpServletRequest("GET", "/api/orders");
        MockFilterChain chain3 = new MockFilterChain();
        filter.doFilter(nonMatching, response, chain3);
        assertInstanceOf(XssRequestWrapper.class, chain3.getRequest());
    }

    @Test
    void defaultConstructor_noExclusions_alwaysWraps() throws ServletException, IOException {
        XssFilter filter = new XssFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any/path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertInstanceOf(XssRequestWrapper.class, filterChain.getRequest());
    }

    @Test
    void nullExcludePaths_treatedAsEmpty() throws ServletException, IOException {
        XssFilter filter = new XssFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertInstanceOf(XssRequestWrapper.class, filterChain.getRequest());
    }
}
