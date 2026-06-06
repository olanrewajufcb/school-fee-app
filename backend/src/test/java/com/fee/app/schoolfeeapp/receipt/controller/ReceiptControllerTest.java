package com.fee.app.schoolfeeapp.receipt.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.receipt.dto.response.ReceiptDetailResponse;
import com.fee.app.schoolfeeapp.receipt.service.ReceiptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptControllerTest {

    @Mock
    private ReceiptService receiptService;

    @InjectMocks
    private ReceiptController receiptController;

    private static final String RECEIPT_NUMBER = "RCP-2026-ABC123";
    private static final UUID PAYMENT_ID = UUID.fromString("f6a7b890-1234-4567-f123-456789012345");

    @Test
    @DisplayName("Should get receipt details successfully")
    void shouldGetReceiptDetailsSuccessfully() {
        ReceiptDetailResponse serviceResponse = receiptDetails();
        when(receiptService.getReceiptDetails(RECEIPT_NUMBER)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(receiptController.getReceipt(RECEIPT_NUMBER))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<ReceiptDetailResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(receiptService).getReceiptDetails(RECEIPT_NUMBER);
    }

    @Test
    @DisplayName("Should propagate receipt details error")
    void shouldPropagateReceiptDetailsError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "RECEIPT_NOT_FOUND",
                "Receipt not found: " + RECEIPT_NUMBER);
        when(receiptService.getReceiptDetails(RECEIPT_NUMBER)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(receiptController.getReceipt(RECEIPT_NUMBER))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(receiptService).getReceiptDetails(RECEIPT_NUMBER);
    }

    @Test
    @DisplayName("Should download receipt PDF with safe headers")
    void shouldDownloadReceiptPdfWithSafeHeaders() {
        String unsafeReceiptNumber = "RCP/2026 ABC123";
        byte[] pdfBytes = "%PDF-test".getBytes(StandardCharsets.US_ASCII);
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(pdfBytes);
        when(receiptService.downloadReceiptPdf(unsafeReceiptNumber)).thenReturn(Mono.just(dataBuffer));

        StepVerifier.create(receiptController.downloadReceiptPdf(unsafeReceiptNumber))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
                    assertThat(response.getHeaders().getContentLength()).isEqualTo(pdfBytes.length);
                    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                            .contains("attachment")
                            .contains("receipt-RCP_2026_ABC123.pdf");
                    assertThat(response.getBody()).isNotNull();
                })
                .verifyComplete();

        verify(receiptService).downloadReceiptPdf(unsafeReceiptNumber);
    }

    private ReceiptDetailResponse receiptDetails() {
        return new ReceiptDetailResponse(
                RECEIPT_NUMBER,
                PAYMENT_ID,
                "Grace International School",
                "12 School Road, Lagos",
                "Ada Parent",
                BigDecimal.valueOf(5000),
                "Five Thousand Naira Only",
                "PAYSTACK",
                Instant.parse("2026-06-05T10:00:00Z"),
                List.of(new ReceiptDetailResponse.BreakdownItem(
                        "Ada Lovelace",
                        "STU260010",
                        null,
                        null,
                        BigDecimal.valueOf(5000))),
                Instant.parse("2026-06-05T10:02:00Z"),
                false,
                false);
    }
}
