package ru.vstu.medsim.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final long slowRequestThresholdMs;

    public RequestLoggingFilter(@Value("${medsim.logging.slow-request-threshold-ms:1500}") long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = RequestLoggingContext.resolveOrGenerateRequestId(request);
        boolean failed = false;

        RequestLoggingContext.bind(request, requestId);
        response.setHeader(RequestLoggingContext.REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int statusCode = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            String principal = resolvePrincipal();

            RequestLoggingContext.updatePrincipal(principal);
            logRequest(request, statusCode, durationMs);
            RequestLoggingContext.clear();
        }
    }

    private void logRequest(HttpServletRequest request, int statusCode, long durationMs) {
        String message = "HTTP request completed: method={}, path={}, status={}, durationMs={}";
        String path = request.getQueryString() == null || request.getQueryString().isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();

        if (statusCode >= 500) {
            log.error(message, request.getMethod(), path, statusCode, durationMs);
            return;
        }

        if (statusCode >= 400) {
            log.warn(message, request.getMethod(), path, statusCode, durationMs);
            return;
        }

        if (durationMs >= slowRequestThresholdMs) {
            log.warn(message, request.getMethod(), path, statusCode, durationMs);
            return;
        }

        if (isReadOnlyMethod(request.getMethod()) || isHighVolumeGameplayEndpoint(request.getRequestURI())) {
            log.debug(message, request.getMethod(), path, statusCode, durationMs);
            return;
        }

        log.info(message, request.getMethod(), path, statusCode, durationMs);
    }

    private boolean isReadOnlyMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isHighVolumeGameplayEndpoint(String requestUri) {
        return requestUri.startsWith("/api/player-sessions/");
    }

    private String resolvePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
