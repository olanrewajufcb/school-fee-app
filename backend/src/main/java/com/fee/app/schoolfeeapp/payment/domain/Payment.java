package com.fee.app.schoolfeeapp.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Data
@Table("payment.payments")
public class Payment {

    @Id
    private UUID id;

    @Column("student_fee_id")
    private UUID studentFeeId;

    @Column("student_id")
    private UUID studentId;

    @Column("school_id")
    private UUID schoolId;

    private BigDecimal amount;

    @Column("payment_method")
    private String paymentMethod;

    @Column("payment_gateway")
    private String paymentGateway;

    @Column("gateway_transaction_ref")
    private String gatewayTransactionRef;

    @Column("gateway_status")
    private String gatewayStatus;

    @Column("gateway_raw_response")
    private JsonNode gatewayRawResponse;

    @Column("payment_mode")
    private String paymentMode;

    @Column("offline_approved_by")
    private UUID offlineApprovedBy;

    @Column("offline_approval_date")
    private Instant offlineApprovalDate;

    private String status;

    @Column("paid_by")
    private UUID paidBy;

    @Column("payer_phone")
    private String payerPhone;

    @Column("payer_name")
    private String payerName;

    private String narration;

    private JsonNode metadata;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    private Instant deletedAt;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Version
    private Long version;

    }
