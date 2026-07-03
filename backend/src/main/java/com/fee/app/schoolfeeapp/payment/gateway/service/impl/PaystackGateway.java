package com.fee.app.schoolfeeapp.payment.gateway.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fee.app.schoolfeeapp.common.exceptions.GatewayException;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.dto.response.BankTransferResponse;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus;
import com.fee.app.schoolfeeapp.payment.gateway.dto.GatewayResponse;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
public class PaystackGateway implements PaymentGateway {

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${payment.paystack.secret-key:sk_test_default}")
    private String secretKey;

    @Value("${payment.paystack.base-url:https://api.paystack.co}")
    private String baseUrl;

    @Value("${payment.paystack.callback-url:https://api.schoolfee.app/api/v1/webhooks/paystack/callback}")
    private String callbackUrl;

    private static final String CURRENCY = "NGN";
    private static final int PAYSTACK_KOBO_MULTIPLIER = 100;

    public PaystackGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String getPaymentMethod() {
        return "PAYSTACK";
    }

    @Override
    public Mono<GatewayResponse> initiatePayment(
            UUID paymentId, String customerEmail, BigDecimal amount, String narration) {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", requireEmail(customerEmail));
        body.put("amount", toKobo(amount));
        body.put("currency", CURRENCY);
        body.put("reference", paymentId.toString());
        body.put("callback_url", callbackUrl);

        // Add metadata for reconciliation
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("payment_id", paymentId.toString());
        metadata.put("narration", narration);
        body.set("metadata", metadata);

        log.info("Initiating Paystack payment: paymentId={}, amount={}, reference={}",
                paymentId, amount, paymentId);

        return webClient.post()
                .uri(baseUrl + "/transaction/initialize")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        this::paystackInitializeError)
                .bodyToMono(JsonNode.class)
                .flatMap(response -> {
                    if (!response.path("status").asBoolean()) {
                        String errorMessage = response.path("message").asText("Payment initialization failed");
                        log.error("Paystack initialization failed: {}", errorMessage);
                        return Mono.error(new SchoolFeeException("PAYSTACK_INIT_FAILED", errorMessage));
                    }

                    JsonNode data = response.path("data");
                    String accessCode = data.path("access_code").asText();
                    String authorizationUrl = data.path("authorization_url").asText();
                    String reference = data.path("reference").asText();

                    log.info("Paystack payment initialized: reference={}, accessCode={}", reference, accessCode);

                    return Mono.just(GatewayResponse.builder()
                            .gatewayTransactionRef(reference)
                            .status("PROCESSING")
                            .message("Paystack payment initialized. Redirect to: " + authorizationUrl)
                            .authorizationUrl(authorizationUrl)
                            .rawResponse(response.toString())
                            .expiresInSeconds(3600) // Paystack gives 1 hour
                            .build());
                });
    }

    private Mono<? extends Throwable> paystackInitializeError(ClientResponse response) {
        return response.bodyToMono(JsonNode.class)
                .defaultIfEmpty(objectMapper.createObjectNode())
                .map(body -> {
                    String message = body.path("message")
                            .asText("Paystack rejected the payment initialization request");
                    log.error("Paystack initialization HTTP {} failed: {}", response.statusCode(), message);
                    return new SchoolFeeException("PAYSTACK_INIT_FAILED", message);
                });
    }

    @Override
    public Mono<GatewayStatus> verifyPayment(String gatewayTransactionRef) {
        log.info("Verifying Paystack payment: reference={}", gatewayTransactionRef);

        return webClient.get()
                .uri(baseUrl + "/transaction/verify/" + gatewayTransactionRef)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    JsonNode data = response.path("data");
                    String status = data.path("status").asText();
                    boolean isSuccess = "success".equalsIgnoreCase(status);

                    BigDecimal amount = BigDecimal.valueOf(
                            data.path("amount").asLong())
                            .divide(BigDecimal.valueOf(PAYSTACK_KOBO_MULTIPLIER));

                    String phoneNumber = data.path("customer")
                            .path("phone").asText(null);

                    return GatewayStatus.builder()
                            .gatewayTransactionRef(gatewayTransactionRef)
                            .gatewayReceiptNumber(data.path("id").asText())
                            .amount(amount)
                            .phoneNumber(phoneNumber)
                            .isSuccess(isSuccess)
                            .resultDescription(data.path("gateway_response").asText())
                            .transactionDate(parseInstant(data.path("paid_at").asText(null)))
                            .build();
                });
    }

    @Override
    public Mono<GatewayCallbackData> handleCallback(String rawPayload) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(rawPayload);

            String event = root.path("event").asText();
            JsonNode data = root.path("data");

            if (!event.startsWith("charge.")) {
                log.debug("Ignoring non-success Paystack event: {}", event);
                return GatewayCallbackData.builder()
                        .isSuccess(false)
                        .resultDescription("Event ignored: " + event)
                        .rawPayload(rawPayload)
                        .build();
            }

            String reference = data.path("reference").asText();
            String status = data.path("status").asText();
            boolean isSuccess = "charge.success".equals(event) && "success".equalsIgnoreCase(status);

            BigDecimal amount = BigDecimal.valueOf(
                    data.path("amount").asLong())
                    .divide(BigDecimal.valueOf(PAYSTACK_KOBO_MULTIPLIER));

            String phoneNumber = data.path("customer")
                    .path("phone").asText(null);

            // Extract our payment ID from metadata
            JsonNode metadata = data.path("metadata");
            String paymentId = metadata.path("payment_id").asText(null);

            log.info("Paystack callback: reference={}, status={}, amount={}, paymentId={}",
                    reference, status, amount, paymentId);

            return GatewayCallbackData.builder()
                    .gatewayTransactionRef(reference)
                    .gatewayReceiptNumber(data.path("id").asText())
                    .amount(amount)
                    .phoneNumber(phoneNumber)
                    .isSuccess(isSuccess)
                    .resultDescription(data.path("gateway_response").asText())
                    .rawPayload(rawPayload)
                    .build();
        });
    }

    @Override
    public Mono<Boolean> isAvailable(UUID schoolId) {
        // Phase 2: Check payment_gateway_configs table per school
        // For MVP, Paystack is available if secret key is configured
        return Mono.just(secretKey != null && !secretKey.isBlank()
                && !secretKey.contains("default"));
    }



    private long toKobo(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SchoolFeeException("INVALID_PAYMENT_AMOUNT", "Amount must be greater than 0", "amount");
        }
        return amount.multiply(BigDecimal.valueOf(PAYSTACK_KOBO_MULTIPLIER))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_CUSTOMER",
                    "A valid parent email is required to initialize Paystack payment",
                    "email");
        }
        String trimmed = email.trim();
        if (!trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_CUSTOMER",
                    "A valid parent email is required to initialize Paystack payment",
                    "email");
        }
        return trimmed;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(value);
    }



    @Override
    public Mono<BankTransferResponse> initiateBankTransfer(
            UUID paymentId, BigDecimal amount, String email, String customerName) {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", email);
        body.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue()); // Kobo
        body.put("reference", paymentId.toString());

        // Request virtual account
        ArrayNode channels = objectMapper.createArrayNode();
        channels.add("bank_transfer");
        body.set("channels", channels);

        return webClient.post()
                .uri(baseUrl + "/transaction/initialize")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> {
                    if (!response.path("status").asBoolean()) {
                        return Mono.error(new GatewayException(
                                "PAYSTACK_ERROR", response.path("message").asText()));
                    }

                    String reference = response.path("data").path("reference").asText();

                    // Now resolve to get the virtual account details
                    return resolveBankTransferDetails(reference, amount);
                });
    }

    @Override
    public Mono<BankTransferResponse> resolveBankTransfer(String gatewayTransactionRef) {
        if (gatewayTransactionRef == null || gatewayTransactionRef.isBlank()) {
            return Mono.just(BankTransferResponse.builder()
                    .reference(null)
                    .accountNumber(null)
                    .accountName(null)
                    .bankName(null)
                    .amount(null)
                    .status("PENDING")
                    .message("Transfer details not yet available. Please wait or try again.")
                    .build());
        }

        return webClient.get()
                .uri(baseUrl + "/transaction/verify/" + gatewayTransactionRef)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    JsonNode data = response.path("data");
                    String status = data.path("status").asText();

                    // Map Paystack status to our status
                    String mappedStatus;
                    if ("success".equalsIgnoreCase(status)) {
                        mappedStatus = "COMPLETED";
                    } else if ("abandoned".equalsIgnoreCase(status)) {
                        mappedStatus = "FAILED";
                    } else {
                        mappedStatus = "PROCESSING";
                    }

                    String accountNumber = data.path("authorization")
                            .path("account_number").asText(null);
                    String accountName = data.path("authorization")
                            .path("account_name").asText(null);
                    String bankName = data.path("authorization")
                            .path("bank").asText(null);

                    BigDecimal amount = BigDecimal.valueOf(
                                    data.path("amount").asLong(0))
                            .divide(BigDecimal.valueOf(100));

                    if (accountNumber == null || accountNumber.isBlank()) {
                        return BankTransferResponse.builder()
                                .reference(gatewayTransactionRef)
                                .status(mappedStatus)
                                .amount(amount)
                                .message("Transfer details are being generated. Please check back shortly.")
                                .build();
                    }

                    return BankTransferResponse.builder()
                            .reference(gatewayTransactionRef)
                            .accountNumber(accountNumber)
                            .accountName(accountName)
                            .bankName(bankName)
                            .amount(amount)
                            .status(mappedStatus)
                            .message(mappedStatus.equals("COMPLETED")
                                    ? "Payment confirmed. Receipt generated."
                                    : "Transfer to the account above. Payment will be confirmed automatically.")
                            .build();
                })
                .onErrorResume(error -> {
                    log.error("Failed to resolve bank transfer: ref={}", gatewayTransactionRef, error);
                    return Mono.just(BankTransferResponse.builder()
                            .reference(gatewayTransactionRef)
                            .status("UNKNOWN")
                            .message("Unable to retrieve transfer details. Please try again.")
                            .build());
                });
    }
    /**
     * After initializing a bank transfer payment, resolve the virtual account details.
     * Paystack generates a unique virtual account per transaction.
     */
    private Mono<BankTransferResponse> resolveBankTransferDetails(
            String reference, BigDecimal amount) {

        return webClient.get()
                .uri(baseUrl + "/transaction/verify/" + reference)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    JsonNode data = response.path("data");

                    String accountNumber = data.path("authorization")
                            .path("account_number").asText(null);
                    String accountName = data.path("authorization")
                            .path("account_name").asText(null);
                    String bankName = data.path("authorization")
                            .path("bank").asText(null);

                    // If no virtual account yet, provide manual transfer details
                    if (accountNumber == null || accountNumber.isBlank()) {
                        return BankTransferResponse.builder()
                                .reference(reference)
                                .accountNumber("Coming soon...")
                                .accountName("Grace International School")
                                .bankName("Paystack Titan")
                                .amount(amount)
                                .status("PENDING")
                                .message("Transfer details will be sent to your email/phone.")
                                .build();
                    }

                    return BankTransferResponse.builder()
                            .reference(reference)
                            .accountNumber(accountNumber)
                            .accountName(accountName)
                            .bankName(bankName)
                            .amount(amount)
                            .status("READY")
                            .message("Transfer to the account above. Payment will be confirmed automatically.")
                            .build();
                });
    }
}
