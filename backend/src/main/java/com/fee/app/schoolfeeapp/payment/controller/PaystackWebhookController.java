package com.fee.app.schoolfeeapp.payment.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/paystack")
@RequiredArgsConstructor
@Slf4j
public class PaystackWebhookController {

    private final PaymentService paymentService;

    @Value("${payment.paystack.secret-key:}")
    private String paystackSecretKey;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * GET /api/v1/webhooks/paystack/callback
     * Handle browser redirect from Paystack after payment.
     */
    @GetMapping("/callback")
    public Mono<ResponseEntity<Void>> handleRedirect(
            @RequestParam(value = "reference", required = false) String reference,
            @RequestParam(value = "trxref", required = false) String trxref) {
        log.info("Paystack redirect received: reference={}, trxref={}", reference, trxref);

        String redirectUrl = frontendUrl + "/dashboard";
        if (reference != null && !reference.isBlank()) {
            redirectUrl += "?reference=" + reference + "&status=success";
        }

        return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create(redirectUrl))
                .build());
    }

    /**
     * POST /api/v1/webhooks/paystack/callback
     * Handle Paystack webhook events.
     * 
     * Security: Validates x-paystack-signature header.
     * IP Whitelist: Only accepts from Paystack IPs (configured at reverse proxy level).
     */
    @PostMapping("/callback")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> handleCallback(
            @RequestBody String rawPayload,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        if (!hasValidSignature(rawPayload, signature)) {
            log.warn("Invalid Paystack signature");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(Map.of("message", "Invalid Paystack signature"))));
        }

        log.info("Paystack webhook received: payload size={}", rawPayload.length());

        return paymentService.handlePaystackWebhook(rawPayload)
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.success(Map.of("message", "Webhook processed successfully"))));
    }

    /**
     * Verify HMAC SHA512 signature.
     */
    private boolean hasValidSignature(String payload, String signature) {
        if (paystackSecretKey == null || paystackSecretKey.isBlank()
                || signature == null || signature.isBlank()) {
            return false;
        }
        String computedSignature = computeHmacSha512(payload, paystackSecretKey);
        return MessageDigest.isEqual(
                computedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmacSha512(String payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute HMAC", e);
            return "";
        }
    }
}
