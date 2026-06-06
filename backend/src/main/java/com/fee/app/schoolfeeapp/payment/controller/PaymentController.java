package com.fee.app.schoolfeeapp.payment.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.response.InitiatePaymentResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.OfflinePaymentResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.PaymentHistoryResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.PaymentStatusResponse;
import com.fee.app.schoolfeeapp.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<InitiatePaymentResponse>>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        return paymentService.initiatePayment(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('PARENT', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'SUPER_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PaymentStatusResponse>>> getPaymentStatus(
            @PathVariable UUID paymentId) {
        return paymentService.getPaymentStatus(paymentId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>>> getPaymentHistory(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return paymentService.getPaymentHistory(studentId, pageable)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PostMapping("/offline")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<OfflinePaymentResponse>>> recordOfflinePayment(
            @Valid @RequestBody OfflinePaymentRequest request) {
        return paymentService.recordOfflinePayment(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }
}