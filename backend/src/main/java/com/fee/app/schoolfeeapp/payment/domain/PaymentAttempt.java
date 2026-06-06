package com.fee.app.schoolfeeapp.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Builder
@Data
@Table("payment.payment_attempts")
public class PaymentAttempt {
    
    @Id
    private UUID id;
    
    @Column("payment_id")
    private UUID paymentId;
    
    @Column("attempt_number")
    private Integer attemptNumber;
    
    @Column("gateway_request")
    private JsonNode gatewayRequest;
    
    @Column("gateway_response")
    private JsonNode gatewayResponse;
    
    @Column("error_message")
    private String errorMessage;
    
    @Column("attempt_at")
    private Instant attemptAt;
    
    @Column("ip_address")
    private InetAddress ipAddress;
    
    @Column("user_agent")
    private String userAgent;

    }
