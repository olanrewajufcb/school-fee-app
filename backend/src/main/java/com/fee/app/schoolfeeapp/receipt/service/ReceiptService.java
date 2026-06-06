package com.fee.app.schoolfeeapp.receipt.service;


import com.fee.app.schoolfeeapp.receipt.dto.request.ShareReceiptRequest;
import com.fee.app.schoolfeeapp.receipt.dto.response.ReceiptDetailResponse;
import com.fee.app.schoolfeeapp.receipt.dto.response.ShareReceiptResponse;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

public interface ReceiptService {

    Mono<ReceiptDetailResponse> getReceiptDetails(String receiptNumber);
    Mono<DataBuffer> downloadReceiptPdf(String receiptNumber);
    Mono<ShareReceiptResponse> shareReceipt(String receiptNumber, ShareReceiptRequest request);
}