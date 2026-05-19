package com.medicology.assessment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PREFIX = "/api/v1/assessment/internal/";
    private static final String TOKEN_HEADER = "X-Internal-Service-Token";

    @Value("${app.internal-service-token:}")
    private String internalServiceToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Internal service token is not configured.");
            return;
        }
        String provided = request.getHeader(TOKEN_HEADER);
        if (!internalServiceToken.equals(provided)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid internal service token.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
