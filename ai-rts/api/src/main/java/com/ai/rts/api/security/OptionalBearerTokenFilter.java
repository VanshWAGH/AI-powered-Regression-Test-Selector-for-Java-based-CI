package com.ai.rts.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * When {@code ai.rts.api-token} is non-blank, requires {@code Authorization: Bearer <token>} for
 * {@code /api/v1/**}. Actuator and OpenAPI UI stay open for health checks and ops tooling.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OptionalBearerTokenFilter extends OncePerRequestFilter {
    @Value("${ai.rts.api-token:}")
    private String configuredToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if (configuredToken == null || configuredToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.equals("Bearer " + configuredToken)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Unauthorized");
    }
}
