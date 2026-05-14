package com.wayt.support;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", exception.getStatus().value(),
                "error", exception.getStatus().getReasonPhrase(),
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(item -> item != null && !item.isBlank())
                .findFirst()
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", 400,
                "error", "Bad Request",
                "message", message
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", 400,
                "error", "Bad Request",
                "message", "입력값이 저장 가능한 범위를 넘었어요. 내용을 줄여 다시 시도해 주세요."
        ));
    }

    @ExceptionHandler({MultipartException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<Map<String, Object>> handleMultipartException(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", 400,
                "error", "Bad Request",
                "message", "Profile image must be 5MB or smaller."
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", 500,
                "error", "Internal Server Error",
                "message", "서버에서 예상하지 못한 오류가 발생했어요."
        ));
    }
}
