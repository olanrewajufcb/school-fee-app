package com.fee.app.schoolfeeapp.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.common.exceptions.SmsSendException;
import com.fee.app.schoolfeeapp.common.utils.PhoneNumberNormalizer;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AfricasTalkingSmsService implements SmsService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${notification.sms.api-key:}")
    private String apiKey;

    @Value("${notification.sms.username:sandbox}")
    private String username;

    @Value("${notification.sms.sender-id:SCHOOLFEE}")
    private String senderId;

    @Value("${notification.sms.default-country-code:+234}")
    private String defaultCountryCode;

    @Value("${notification.sms.enabled:false}")
    private boolean enabled;

    private static final String AT_BASE_URL = "https://api.africastalking.com";
    private static final String AT_USERNAME = "sandbox";

    public AfricasTalkingSmsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(AT_BASE_URL)
                .build();
    }

    /**
     * Send SMS via Africa's Talking.
     *
     * @param phoneNumber Recipient phone in format 234XXXXXXXXXX
     * @param message SMS body (max 160 chars per segment)
     * @return Mono that completes when SMS is accepted by the gateway
     */
    @Override
    public Mono<Void> send(String phoneNumber, String message) {
        if (!enabled) {
            log.info("SMS disabled. Would send to {}: {}", phoneNumber, message);
            return Mono.empty();
        }

        // Validate phone number format
        String formattedPhone = PhoneNumberNormalizer.normalize(phoneNumber);
        if (formattedPhone == null) {
            log.error("Invalid phone number format: {}", phoneNumber);
            return Mono.empty();
        }

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("username", username);
        requestBody.put("to", new String[]{formattedPhone});
        requestBody.put("message", message);
        requestBody.put("from", senderId);

        log.info("Sending SMS to {}: {}", formattedPhone, maskMessage(message));

        return webClient.post()
                .uri("/version1/messaging")
                .header("apiKey", apiKey)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status != HttpStatus.CREATED,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("SMS API error: {}", body);
                                    return Mono.error(new SmsSendException(
                                            "SMS send failed with status: " + response.statusCode()));
                                })
                )
                .bodyToMono(String.class)
                .flatMap(this::parseSmsResponse)
                .doOnSuccess(result -> log.info("SMS sent successfully to {}", formattedPhone))
                .doOnError(error -> log.error("SMS send failed: {}", error.getMessage()))
                .then();
    }

    /**
     * Get SMS balance from Africa's Talking.
     */
    @Override
    public Mono<Integer> getBalance() {
        if (!enabled) {
            return Mono.just(2500);
        }

        return webClient.get()
                .uri("/version1/user?username=" + username)
                .header("apiKey", apiKey)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(this::parseBalanceResponse)
                .onErrorResume(error -> {
                    log.error("Failed to get SMS balance: {}", error.getMessage());
                    return Mono.just(-1);
                });
    }

    /**
     * Parse SMS send response from Africa's Talking.
     *
     * Success response:
     * {
     *   "SMSMessageData": {
     *     "Message": "Sent to 1/1 Total Cost: KES 0.8000",
     *     "Recipients": [{
     *       "statusCode": 101,
     *       "number": "+2348031234567",
     *       "status": "Success",
     *       "cost": "KES 0.8000",
     *       "messageId": "ATXid_abc123"
     *     }]
     *   }
     * }
     */
    private Mono<Void> parseSmsResponse(String responseBody) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("SMSMessageData");
            JsonNode recipients = data.path("Recipients");

            if (recipients.isArray() && !recipients.isEmpty()) {
                JsonNode firstRecipient = recipients.get(0);
                int statusCode = firstRecipient.path("statusCode").asInt();
                String status = firstRecipient.path("status").asText();

                // Status code 101 = Success
                if (statusCode == 101) {
                    log.debug("SMS delivered. MessageId: {}, Cost: {}",
                            firstRecipient.path("messageId").asText(),
                            firstRecipient.path("cost").asText());
                    return null; // Success
                } else {
                    throw new SmsSendException(
                            "SMS rejected by gateway. Status: " + status + " (" + statusCode + ")");
                }
            }

            throw new SmsSendException("No recipients in SMS response");
        }).then();
    }

    /**
     * Parse balance response.
     *
     * Response:
     * {
     *   "UserData": {
     *     "balance": "KES 2500.0000"
     *   }
     * }
     */
    private Mono<Integer> parseBalanceResponse(String responseBody) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(responseBody);
            String balanceStr = root.path("UserData").path("balance").asText();

            // Extract numeric value from "KES 2500.0000"
            String numericPart = balanceStr.replaceAll("[^0-9.]", "");
            return (int) Double.parseDouble(numericPart);
        });
    }



    /**
     * Mask message for logging (don't log full SMS content).
     */
    private String maskMessage(String message) {
        if (message == null || message.length() <= 20) {
            return message;
        }
        return message.substring(0, 20) + "...";
    }
}
