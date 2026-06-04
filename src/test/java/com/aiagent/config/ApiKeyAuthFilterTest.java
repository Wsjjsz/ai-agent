package com.aiagent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyAuthFilterTest {

    @Test
    void allowsRequestsWhenApiKeyIsNotConfigured() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "");
        CountingFilterChain chain = new CountingFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/session/list"),
                new MockHttpServletResponse(), chain);

        assertTrue(chain.called);
    }

    @Test
    void rejectsRequestsWithoutMatchingApiKey() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "secret");
        CountingFilterChain chain = new CountingFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/session/list"), response, chain);

        assertEquals(401, response.getStatus());
        assertFalse(chain.called);
    }

    @Test
    void allowsRequestsWithMatchingApiKey() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "secret");
        CountingFilterChain chain = new CountingFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session/list");
        request.addHeader("X-API-Key", "secret");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertTrue(chain.called);
    }

    @Test
    void healthEndpointBypassesApiKey() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "secret");
        CountingFilterChain chain = new CountingFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/health"),
                new MockHttpServletResponse(), chain);

        assertTrue(chain.called);
    }

    private static class CountingFilterChain implements FilterChain {

        private boolean called;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called = true;
        }
    }
}
