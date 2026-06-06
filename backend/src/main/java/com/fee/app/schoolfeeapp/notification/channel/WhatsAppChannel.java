package com.fee.app.schoolfeeapp.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * WhatsApp Business API channel.
 * 
 * Uses WhatsApp Cloud API (Meta) to send template messages.
 * Phase 1: Uses pre-approved template messages.
 * Phase 2: Dynamic template creation and rich media support.
 */
@Component
@Slf4j
public class WhatsAppChannel implements NotificationChannel {

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${notification.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${notification.whatsapp.access-token:}")
    private String accessToken;

    @Value("${notification.whatsapp.business-account-id:}")
    private String businessAccountId;

    @Value("${notification.whatsapp.enabled:false}")
    private boolean enabled;

    private static final String META_API_URL = "https://graph.facebook.com/v18.0";
    private static final BigDecimal COST_PER_MESSAGE = BigDecimal.valueOf(0.50); // ~₦0.50 per conversation

    public WhatsAppChannel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(META_API_URL).build();
    }

    @Override
    public String getChannel() {
        return "WHATSAPP";
    }

    @Override
    public Mono<ChannelResult> send(String recipient, String message, String contextId) {
        if (!enabled) {
            log.info("WhatsApp disabled. Would send to {}: {}", recipient, message);
            return Mono.just(ChannelResult.builder()
                    .channel("WHATSAPP")
                    .success(true)
                    .messageId("DISABLED-" + contextId)
                    .build());
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", formatPhoneNumber(recipient));

        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("preview_url", false);
        textNode.put("body", message);
        body.set("text", textNode);

        log.info("Sending WhatsApp to {}: {}", recipient,
                message.substring(0, Math.min(50, message.length())));

        return webClient.post()
                .uri("/{phoneNumberId}/messages", phoneNumberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    String messageId = response.path("messages").get(0)
                            .path("id").asText("unknown");
                    return ChannelResult.builder()
                            .channel("WHATSAPP")
                            .messageId(messageId)
                            .success(true)
                            .rawResponse(response.toString())
                            .build();
                })
                .onErrorResume(error -> {
                    log.error("WhatsApp send failed to {}: {}", recipient, error.getMessage());
                    return Mono.just(ChannelResult.builder()
                            .channel("WHATSAPP")
                            .success(false)
                            .errorMessage(error.getMessage())
                            .build());
                });
    }

    @Override
    public BigDecimal getCostPerMessage() {
        return COST_PER_MESSAGE;
    }

    @Override
    public Mono<Integer> getBalance() {
        // WhatsApp doesn't have a prepaid balance — it's postpaid
        return Mono.just(-1);
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return Mono.just(enabled && phoneNumberId != null && !phoneNumberId.isBlank()
                && accessToken != null && !accessToken.isBlank());
    }

    @Override
    public String getProviderName() {
        return "WHATSAPP_CLOUD_API";
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("234")) return digits;
        if (digits.startsWith("0")) return "234" + digits.substring(1);
        return digits;
    }
}