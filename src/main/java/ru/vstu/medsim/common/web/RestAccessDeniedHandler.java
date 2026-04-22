package ru.vstu.medsim.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        log.warn(
                "Forbidden request: method={}, path={}, message={}",
                request.getMethod(),
                resolvePath(request),
                accessDeniedException.getMessage()
        );

        String requestId = RequestLoggingContext.getCurrentRequestId(request);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.FORBIDDEN,
                "Доступ запрещён.",
                resolvePath(request),
                requestId
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
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
