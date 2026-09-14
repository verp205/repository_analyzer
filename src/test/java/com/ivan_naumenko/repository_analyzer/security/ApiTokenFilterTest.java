package com.ivan_naumenko.repository_analyzer.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenFilterTest {

    @Test
    void allowsEverythingWhenTokenIsNotConfigured() throws Exception {
        MockFilterChain chain = run("", "GET", "/api/analysis-tasks", request -> {
        });

        assertNotNull(chain.getRequest());
    }

    @Test
    void rejectsApiRequestsWithoutValidToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analysis-tasks");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader(ApiTokenFilter.TOKEN_HEADER, "wrong");

        filter("secret").doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getContentAsString().contains("API-токен"));
    }

    @Test
    void acceptsTokenFromHeaderBearerOrQueryParameter() throws Exception {
        assertNotNull(run("secret", "POST", "/api/analysis-tasks",
                request -> request.addHeader(ApiTokenFilter.TOKEN_HEADER, "secret")).getRequest());
        assertNotNull(run("secret", "GET", "/api/reports/1",
                request -> request.addHeader("Authorization", "Bearer secret")).getRequest());
        assertNotNull(run("secret", "GET", "/api/reports/1/events",
                request -> request.setParameter(ApiTokenFilter.TOKEN_PARAMETER, "secret")).getRequest());
    }

    @Test
    void ignoresNonApiPathsAndPreflightRequests() throws Exception {
        assertNotNull(run("secret", "GET", "/actuator/health", request -> {
        }).getRequest());
        assertNotNull(run("secret", "OPTIONS", "/api/analysis-tasks", request -> {
        }).getRequest());
    }

    private MockFilterChain run(
            String token,
            String method,
            String path,
            Consumer<MockHttpServletRequest> customizer
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        customizer.accept(request);
        MockFilterChain chain = new MockFilterChain();
        filter(token).doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    private ApiTokenFilter filter(String token) {
        return new ApiTokenFilter(new SecurityProperties(false, List.of(), token));
    }
}
