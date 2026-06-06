package com.fee.app.schoolfeeapp.receipt.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.receipt.dto.request.ShareReceiptRequest;
import com.fee.app.schoolfeeapp.receipt.dto.response.ReceiptDetailResponse;
import com.fee.app.schoolfeeapp.receipt.dto.response.ShareReceiptResponse;
import com.fee.app.schoolfeeapp.receipt.service.ReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    /**
     * GET /api/v1/receipts/{receiptNumber}
     * Get receipt details.
     */
    @GetMapping("/{receiptNumber}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<ApiResponse<ReceiptDetailResponse>>> getReceipt(
            @PathVariable String receiptNumber) {
        return receiptService.getReceiptDetails(receiptNumber)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/receipts/{receiptNumber}/pdf
     * Download receipt as PDF.
     */
    @GetMapping("/{receiptNumber}/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<DataBuffer>> downloadReceiptPdf(
            @PathVariable String receiptNumber) {
        return receiptService.downloadReceiptPdf(receiptNumber)
                .map(pdfData -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename("receipt-" + safeFilename(receiptNumber) + ".pdf")
                                .build()
                                .toString())
                        .contentType(MediaType.APPLICATION_PDF)
                        .contentLength(pdfData.readableByteCount())
                        .body(pdfData));
    }

    /**
     * POST /api/v1/receipts/{receiptNumber}/share
     * Share receipt via SMS, email, or WhatsApp.
     */
    @PostMapping("/{receiptNumber}/share")
    @PreAuthorize("hasAnyRole('PARENT', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<ShareReceiptResponse>>> shareReceipt(
            @PathVariable String receiptNumber,
            @Valid @RequestBody ShareReceiptRequest request) {
        return receiptService.shareReceipt(receiptNumber, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
