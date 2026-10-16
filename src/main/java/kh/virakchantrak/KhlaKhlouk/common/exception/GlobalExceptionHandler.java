package kh.virakchantrak.KhlaKhlouk.common.exception;

import kh.virakchantrak.KhlaKhlouk.common.response.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
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
}
