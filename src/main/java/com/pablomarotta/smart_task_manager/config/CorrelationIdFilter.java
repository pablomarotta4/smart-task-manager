package com.pablomarotta.smart_task_manager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = CorrelationIdPolicy.resolve(
                request.getHeader(CorrelationIdPolicy.HEADER_NAME)
        );
        String previousCorrelationId = MDC.get(CorrelationIdPolicy.MDC_KEY);
        response.setHeader(CorrelationIdPolicy.HEADER_NAME, correlationId);
        MDC.put(CorrelationIdPolicy.MDC_KEY, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            restorePreviousCorrelationId(previousCorrelationId);
        }
    }

    private void restorePreviousCorrelationId(String previousCorrelationId) {
        if (previousCorrelationId == null) {
            MDC.remove(CorrelationIdPolicy.MDC_KEY);
            return;
        }
        MDC.put(CorrelationIdPolicy.MDC_KEY, previousCorrelationId);
    }
}
