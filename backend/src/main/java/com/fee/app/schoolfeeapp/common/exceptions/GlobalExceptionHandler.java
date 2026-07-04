package com.fee.app.schoolfeeapp.common.exceptions;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(SchoolFeeException.class)
    public ResponseEntity<ApiResponse<Void>> handleSchoolFeeException(SchoolFeeException ex) {
        log.error("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .field(ex.getField())
                .build();
        
        HttpStatus status = switch (ex.getErrorCode()) {
            case "RESOURCE_NOT_FOUND", "SCHOOL_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DUPLICATE_RESOURCE", "STALE_RESOURCE" -> HttpStatus.CONFLICT;
            case "INVALID_STATE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        
        return ResponseEntity.status(status)
                .body(ApiResponse.error(List.of(error)));
    }
    
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(WebExchangeBindException ex) {
        List<ErrorResponse> errors = ex.getFieldErrors().stream()
                .map(this::toErrorResponse)
                .toList();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errors));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("ACCESS_DENIED")
                .message("You do not have permission to perform this action")
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(List.of(error)));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKeyException(DuplicateKeyException ex) {
        log.warn("Duplicate resource rejected: {}", ex.getMostSpecificCause().getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .code("DUPLICATE_RESOURCE")
                .message("This record has already been saved. Refresh the page to view the latest version.")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(List.of(error)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Database integrity error", ex);

        ErrorResponse error = ErrorResponse.builder()
                .code("DATA_INTEGRITY_ERROR")
                .message("We could not save this request safely. Please refresh the page and try again.")
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(List.of(error)));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        
        ErrorResponse error = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(List.of(error)));
    }
    
    private ErrorResponse toErrorResponse(FieldError fieldError) {
        return ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message(fieldError.getDefaultMessage())
                .field(fieldError.getField())
                .build();
    }
}
