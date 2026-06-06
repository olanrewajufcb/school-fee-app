package com.fee.app.schoolfeeapp.fee.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table("fee.ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id ;

    @Column("student_fee_id")
    private UUID studentFeeId;

    @Column("school_id")
    private UUID schoolId ;

    @Column("entry_type")
    private String entryType;

    @Column("student_id")
    private UUID studentId;

    private BigDecimal amount;

    private BigDecimal runningBalance;

    @Column("balance_after")
    private BigDecimal balanceAfter;

    @Column("source_entity_type")
    private String sourceEntityType;

    @Column("source_entity_id")
    private UUID sourceEntityId;

    @Column("related_entity_type")
    private String relatedEntityType;

    @Column("related_entity_id")
    private UUID relatedEntityId;

    private String description;

    @Column("transaction_date")
    private Instant transactionDate;

    @Column("idempotency_key")
    private UUID idempotencyKey;

    @Column("created_at")
    private Instant createdAt;

    @Column("recorded_by")
    private UUID recordedBy;

    @Column("system_action")
    private String systemAction;

    @Column("deleted_at")
    private Instant deletedAt;

    @Column("is_reversed")
    private Boolean isReversed;

    @Version
    @Column("version")
    private Integer version;

}
