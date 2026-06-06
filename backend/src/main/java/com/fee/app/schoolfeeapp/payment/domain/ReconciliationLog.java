package com.fee.app.schoolfeeapp.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@Table("payment.reconciliation_logs")
public class ReconciliationLog {

  @Id
  private UUID id;

  @Column("school_id")
  private UUID schoolId;

  @Column("gateway_name")
  private String gatewayName;

  @Column("reconciliation_date")
  private LocalDate reconciliationDate;

  @Column("total_expected")
  private BigDecimal totalExpected;

  @Column("total_received")
  private BigDecimal totalReceived;

  private BigDecimal discrepancy;

  @Column("unmatched_transactions")
  private JsonNode unmatchedTransactions;

  private String status;

  @Column("resolved_by")
  private UUID resolvedBy;

  @Column("resolution_notes")
  private String resolutionNotes;

  @Column("created_at")
  private Instant createdAt;
}