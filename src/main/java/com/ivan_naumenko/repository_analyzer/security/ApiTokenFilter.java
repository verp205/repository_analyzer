package com.ivan_naumenko.repository_analyzer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Простая защита API общим токеном. Включается настройкой
 * repository-analyzer.security.api-token. Токен принимается из заголовка
 * X-Api-Token, заголовка Authorization: Bearer или параметра access_token
 * (последний нужен для SSE, где браузер не умеет задавать заголовки).
 */
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    static final String TOKEN_HEADER = "X-Api-Token";
    static final String TOKEN_PARAMETER = "access_token";

    private static final Pattern LOCAL_ORIGIN = Pattern.compile(
            "^http://(localhost|127\\.0\\.0\\.1)(:\\d+)?$"
    );

    private final SecurityProperties properties;

    public ApiTokenFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return properties.apiToken().isEmpty()
                || !path.startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String provided = extractToken(request);
        if (provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                properties.apiToken().getBytes(StandardCharsets.UTF_8)
        )) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin != null && LOCAL_ORIGIN.matcher(origin).matches()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"status\":401,\"message\":\"Требуется действительный API-токен\"}"
        );
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(TOKEN_HEADER);
        if (header != null && !header.isBlank()) {
            return header.strip();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).strip();
        }
        String parameter = request.getParameter(TOKEN_PARAMETER);
        return parameter == null || parameter.isBlank() ? null : parameter.strip();
    }
}
