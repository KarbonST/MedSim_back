package ru.vstu.medsim.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String message = exception.getReason() != null && !exception.getReason().isBlank()
                ? exception.getReason()
                : defaultMessageFor(statusCode);

        logHandledException(request, statusCode, message, exception, false);
        return buildResponse(statusCode, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldValidationMessage)
                .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "Проверьте корректность параметров запроса.";
        }

        logHandledException(request, HttpStatus.BAD_REQUEST, message, exception, false);
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        String message = "Некорректный JSON или формат тела запроса.";
        logHandledException(request, HttpStatus.BAD_REQUEST, message, exception, false);
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String message = "Внутренняя ошибка сервера. Проверьте requestId в логах.";
        logHandledException(request, HttpStatus.INTERNAL_SERVER_ERROR, message, exception, true);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatusCode statusCode,
            String message,
            HttpServletRequest request
    ) {
        String path = resolvePath(request);
        String requestId = RequestLoggingContext.getCurrentRequestId(request);
        ApiErrorResponse body = ApiErrorResponse.of(statusCode, message, path, requestId);

        return ResponseEntity.status(statusCode)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(RequestLoggingContext.REQUEST_ID_HEADER, requestId == null ? "" : requestId)
                .body(body);
    }

    private void logHandledException(
            HttpServletRequest request,
            HttpStatusCode statusCode,
            String message,
            Exception exception,
            boolean withStackTrace
    ) {
        String logMessage = "API request failed: method={}, path={}, status={}, errorType={}, message={}";
        String path = resolvePath(request);
        String errorType = exception.getClass().getSimpleName();

        if (withStackTrace || statusCode.value() >= 500) {
            log.error(logMessage, request.getMethod(), path, statusCode.value(), errorType, message, exception);
            return;
        }

        if (statusCode.value() == HttpStatus.UNAUTHORIZED.value() || statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            log.warn(logMessage, request.getMethod(), path, statusCode.value(), errorType, message);
            return;
        }

        log.info(logMessage, request.getMethod(), path, statusCode.value(), errorType, message);
    }

    private String resolvePath(HttpServletRequest request) {
        return request.getQueryString() == null || request.getQueryString().isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
    }

    private String defaultMessageFor(HttpStatusCode statusCode) {
        HttpStatus httpStatus = HttpStatus.resolve(statusCode.value());
        return httpStatus != null ? httpStatus.getReasonPhrase() : "Ошибка обработки запроса.";
    }

    private String toFieldValidationMessage(FieldError error) {
        return "%s: %s".formatted(error.getField(), error.getDefaultMessage());
    }
}
