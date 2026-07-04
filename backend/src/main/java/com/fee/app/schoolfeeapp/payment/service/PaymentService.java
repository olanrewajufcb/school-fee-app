package com.fee.app.schoolfeeapp.payment.service;


import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.payment.dto.request.BankTransferRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.response.*;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaymentService {

    Mono<InitiatePaymentResponse> initiatePayment(InitiatePaymentRequest request);
    Mono<PaymentStatusResponse> getPaymentStatus(UUID paymentId);
    Mono<PaymentStatusResponse> getPaymentStatusByReference(String gatewayTransactionRef);
    Mono<PageResponse<PaymentHistoryResponse>> getPaymentHistory(UUID studentId, Pageable pageable);
    Mono<OfflinePaymentResponse> recordOfflinePayment(OfflinePaymentRequest request);
    Mono<Void> handlePaystackWebhook(String rawPayload);
    Mono<BankTransferResponse> initiateBankTransfer(BankTransferRequest request);
    Mono<BankTransferResponse> getBankTransferDetails(UUID paymentId);

}
