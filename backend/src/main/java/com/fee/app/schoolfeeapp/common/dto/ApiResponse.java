package com.fee.app.schoolfeeapp.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private boolean success;
    private T data;
    private Meta meta;
    private Object errors;
    private Instant timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
    
    public static <T> ApiResponse<T> success(T data, Meta meta) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(meta)
                .timestamp(Instant.now())
                .build();
    }
    
    public static <T> ApiResponse<T> error(Object errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}