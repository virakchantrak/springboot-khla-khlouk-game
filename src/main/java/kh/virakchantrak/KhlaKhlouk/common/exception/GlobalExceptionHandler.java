package kh.virakchantrak.KhlaKhlouk.common.exception;

import kh.virakchantrak.KhlaKhlouk.common.response.ApiError;
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
}
