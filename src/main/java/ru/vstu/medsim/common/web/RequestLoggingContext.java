package ru.vstu.medsim.common.web;

import org.slf4j.MDC;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class RequestLoggingContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestLoggingContext.class.getName() + ".requestId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String PRINCIPAL_MDC_KEY = "principal";
    public static final String CLIENT_IP_MDC_KEY = "clientIp";

    private static final String UNKNOWN_CLIENT_IP = "unknown";
    private static final String ANONYMOUS_PRINCIPAL = "anonymous";

    private RequestLoggingContext() {
    }

    public static String resolveOrGenerateRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(REQUEST_ID_HEADER);
        if (headerValue != null) {
            String normalized = headerValue.trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        return UUID.randomUUID().toString();
    }

    public static void bind(HttpServletRequest request, String requestId) {
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(CLIENT_IP_MDC_KEY, resolveClientIp(request));
        MDC.put(PRINCIPAL_MDC_KEY, ANONYMOUS_PRINCIPAL);
    }

    public static void updatePrincipal(String principal) {
        String normalized = principal == null || principal.isBlank() ? ANONYMOUS_PRINCIPAL : principal.trim();
        MDC.put(PRINCIPAL_MDC_KEY, normalized);
    }

    public static void clear() {
        MDC.remove(REQUEST_ID_MDC_KEY);
        MDC.remove(PRINCIPAL_MDC_KEY);
        MDC.remove(CLIENT_IP_MDC_KEY);
    }

    public static String getCurrentRequestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value instanceof String requestId ? requestId : null;
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0) {
                String clientIp = parts[0].trim();
                if (!clientIp.isEmpty()) {
                    return clientIp;
                }
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return remoteAddr;
        }

        return UNKNOWN_CLIENT_IP;
    }
}
