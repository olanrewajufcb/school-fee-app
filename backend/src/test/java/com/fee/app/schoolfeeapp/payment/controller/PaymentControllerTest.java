package com.fee.app.schoolfeeapp.payment.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.response.InitiatePaymentResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.OfflinePaymentResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.PaymentHistoryResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.PaymentStatusResponse;
import com.fee.app.schoolfeeapp.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private static final UUID STUDENT_FEE_ID =
            UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");
    private static final UUID PAYMENT_ID =
            UUID.fromString("f6a7b890-1234-4567-f123-456789012345");

    @Test
    @DisplayName("Should initiate payment successfully")
    void shouldInitiatePaymentSuccessfully() {
        InitiatePaymentRequest request = validRequest();
        InitiatePaymentResponse serviceResponse = new InitiatePaymentResponse(
                PAYMENT_ID,
                "PROCESSING",
                "PAYSTACK",
                BigDecimal.valueOf(5000),
                "Paystack checkout initialized",
                "paystack-ref-123",
                3600);
        when(paymentService.initiatePayment(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(paymentController.initiatePayment(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<InitiatePaymentResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(paymentService).initiatePayment(request);
    }

    @Test
    @DisplayName("Should propagate initiate payment error")
    void shouldPropagateInitiatePaymentError() {
        InitiatePaymentRequest request = validRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "OVERPAYMENT",
                "Amount exceeds available balance",
                "amount");
        when(paymentService.initiatePayment(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(paymentController.initiatePayment(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(paymentService).initiatePayment(request);
    }

    @Test
    @DisplayName("Should get payment status successfully")
    void shouldGetPaymentStatusSuccessfully() {
        PaymentStatusResponse serviceResponse = new PaymentStatusResponse(
                PAYMENT_ID,
                "COMPLETED",
                BigDecimal.valueOf(5000),
                "PAYSTACK",
                "paystack-ref-123",
                Instant.now(),
                new PaymentStatusResponse.ReceiptInfo(
                        "RCP-2026-ABC123",
                        "/api/v1/receipts/RCP-2026-ABC123/pdf",
                        List.of()));
        when(paymentService.getPaymentStatus(PAYMENT_ID)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(paymentController.getPaymentStatus(PAYMENT_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(responseEntity.getBody()).isNotNull();
                    assertThat(responseEntity.getBody().isSuccess()).isTrue();
                    assertThat(responseEntity.getBody().getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(paymentService).getPaymentStatus(PAYMENT_ID);
    }

    @Test
    @DisplayName("Should get payment history successfully")
    void shouldGetPaymentHistorySuccessfully() {
        PageResponse<PaymentHistoryResponse> serviceResponse = new PageResponse<>(
                List.of(new PaymentHistoryResponse(
                        PAYMENT_ID,
                        Instant.now(),
                        BigDecimal.valueOf(5000),
                        "PAYSTACK",
                        "COMPLETED",
                        "Fee payment",
                        "RCP-2026-ABC123")),
                0,
                10,
                1,
                1);
        when(paymentService.getPaymentHistory(org.mockito.Mockito.eq(null), org.mockito.Mockito.any(Pageable.class)))
                .thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(paymentController.getPaymentHistory(null, 0, 10))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(responseEntity.getBody()).isNotNull();
                    assertThat(responseEntity.getBody().isSuccess()).isTrue();
                    assertThat(responseEntity.getBody().getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(paymentService).getPaymentHistory(
                org.mockito.Mockito.eq(null),
                org.mockito.Mockito.argThat(pageable ->
                        pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
    }

    @Test
    @DisplayName("Should record offline payment successfully")
    void shouldRecordOfflinePaymentSuccessfully() {
        OfflinePaymentRequest request = offlineRequest();
        OfflinePaymentResponse serviceResponse = new OfflinePaymentResponse(
                PAYMENT_ID,
                "COMPLETED",
                "RCP-2026-ABC123",
                "Bursar");
        when(paymentService.recordOfflinePayment(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(paymentController.recordOfflinePayment(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(responseEntity.getBody()).isNotNull();
                    assertThat(responseEntity.getBody().isSuccess()).isTrue();
                    assertThat(responseEntity.getBody().getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(paymentService).recordOfflinePayment(request);
    }

    @Test
    @DisplayName("Should propagate offline payment error")
    void shouldPropagateOfflinePaymentError() {
        OfflinePaymentRequest request = offlineRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "OVERPAYMENT",
                "Amount exceeds available balance",
                "amount");
        when(paymentService.recordOfflinePayment(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(paymentController.recordOfflinePayment(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(paymentService).recordOfflinePayment(request);
    }

    private InitiatePaymentRequest validRequest() {
        return new InitiatePaymentRequest(
                List.of(STUDENT_FEE_ID),
                "PAYSTACK",
                "08012345678",
                BigDecimal.valueOf(5000),
                null);
    }

    private OfflinePaymentRequest offlineRequest() {
        return new OfflinePaymentRequest(
                STUDENT_FEE_ID,
                BigDecimal.valueOf(5000),
                "CASH",
                Instant.now(),
                "Bursar",
                "Paid at office",
                true);
    }
}
