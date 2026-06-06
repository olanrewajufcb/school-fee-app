package com.fee.app.schoolfeeapp.payment.gateway.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus;
import com.fee.app.schoolfeeapp.payment.gateway.dto.GatewayResponse;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
            UUID paymentId, String phoneNumber, BigDecimal amount, String narration) {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", "payer-" + paymentId + "@schoolfee.app");
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
                            .rawResponse(response.toString())
                            .expiresInSeconds(3600) // Paystack gives 1 hour
                            .build());
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

    // ========================================================================
    // ADDITIONAL PAYSTACK-SPECIFIC METHODS
    // ========================================================================

    /**
     * Generate a checkout URL for hosted payment page.
     * Useful for web-based payments where the parent is redirected to Paystack.
     */
    public Mono<String> getCheckoutUrl(UUID paymentId, BigDecimal amount, String email, String customerName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", email);
        body.put("amount", toKobo(amount));
        body.put("currency", CURRENCY);
        body.put("reference", paymentId.toString());
        body.put("callback_url", callbackUrl);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("payment_id", paymentId.toString());
        body.set("metadata", metadata);

        return webClient.post()
                .uri(baseUrl + "/transaction/initialize")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response.path("data").path("authorization_url").asText());
    }

    /**
     * List banks for bank transfer option.
     */
    public Mono<JsonNode> listBanks() {
        return webClient.get()
                .uri(baseUrl + "/bank?currency=" + CURRENCY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Resolve account number for bank transfer verification.
     */
    public Mono<JsonNode> resolveAccountNumber(String accountNumber, String bankCode) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(baseUrl + "/bank/resolve")
                        .queryParam("account_number", accountNumber)
                        .queryParam("bank_code", bankCode)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Refund a payment.
     */
    public Mono<GatewayResponse> refund(String transactionReference, BigDecimal amount) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("transaction", transactionReference);

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            body.put("amount", toKobo(amount));
        }

        return webClient.post()
                .uri(baseUrl + "/refund")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> GatewayResponse.builder()
                        .gatewayTransactionRef(response.path("data").path("id").asText())
                        .status(response.path("data").path("status").asText())
                        .message(response.path("message").asText())
                        .rawResponse(response.toString())
                        .expiresInSeconds(0)
                        .build());
    }

    private long toKobo(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SchoolFeeException("INVALID_PAYMENT_AMOUNT", "Amount must be greater than 0", "amount");
        }
        return amount.multiply(BigDecimal.valueOf(PAYSTACK_KOBO_MULTIPLIER))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(value);
    }
}
