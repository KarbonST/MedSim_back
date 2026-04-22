package ru.vstu.medsim.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        log.warn(
                "Unauthorized request: method={}, path={}, message={}",
                request.getMethod(),
                resolvePath(request),
                authException.getMessage()
        );
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"MedSim\"");
        writeResponse(request, response, HttpStatus.UNAUTHORIZED, "Требуется авторизация.");
    }

    private void writeResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        String requestId = RequestLoggingContext.getCurrentRequestId(request);
        ApiErrorResponse body = ApiErrorResponse.of(status, message, resolvePath(request), requestId);

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(RequestLoggingContext.REQUEST_ID_HEADER, requestId == null ? "" : requestId);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolvePath(HttpServletRequest request) {
        return request.getQueryString() == null || request.getQueryString().isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
    }
}
