package kh.virakchantrak.KhlaKhlouk.common.exception;

import kh.virakchantrak.KhlaKhlouk.common.response.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// Extends Spring's handler for ITS OWN exceptions (malformed JSON, bad path values,
// wrong HTTP method, unknown URL...) so they come out in the ApiError format as well.
// It is not a catch-all: security and unknown exceptions are not touched.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(
            BusinessException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(
                        new ApiError(
                                exception.getCode(),
                                exception.getMessage(),
                                Map.of()
                        )
                );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.requireNonNullElse(
                                error.getDefaultMessage(), "Invalid value"),
                        (first, second) -> first
                ));

        return ResponseEntity
                .badRequest()
                .body(
                        new ApiError(
                                "VALIDATION_ERROR",
                                "Request validation failed",
                                errors
                        )
                );
    }

    // every other Spring MVC exception ends up here: fixed text, nothing from the exception
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        if (statusCode.is5xxServerError()) {
            log.error("Unexpected error while handling a request", exception);
        }

        return ResponseEntity
                .status(statusCode)
                .headers(headers)
                .body(new ApiError(codeFor(statusCode), messageFor(statusCode), Map.of()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLockingException(
            OptimisticLockingFailureException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        new ApiError(
                                "CONCURRENT_UPDATE",
                                "The resource was modified by another request",
                                Map.of()
                        )
                );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        // the exception message contains SQL and constraint names,
        // so it is deliberately NOT sent to the client
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        new ApiError(
                                "DATA_CONFLICT",
                                "The request conflicts with existing data",
                                Map.of()
                        )
                );
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidSort(PropertyReferenceException exception) {
        return invalidSortResponse();
    }

    // depending on the call path, Spring may wrap the same problem in this exception
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiError> handleInvalidDataAccessApiUsage(
            InvalidDataAccessApiUsageException exception
    ) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PropertyReferenceException) {
                return invalidSortResponse();
            }
        }

        // anything else is a real bug: don't hide it
        throw exception;
    }

    private ResponseEntity<ApiError> invalidSortResponse() {
        // the rejected property name is deliberately not echoed back
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_SORT", "Invalid sort parameter", Map.of()));
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_ERROR";
        };
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Malformed or invalid request";
            case 404 -> "Resource not found";
            case 405 -> "HTTP method not supported for this endpoint";
            case 406 -> "Requested content type is not available";
            case 413 -> "Request is too large";
            case 415 -> "Content type is not supported";
            default -> status.is5xxServerError()
                    ? "Unexpected server error"
                    : "The request could not be processed";
        };
    }
}