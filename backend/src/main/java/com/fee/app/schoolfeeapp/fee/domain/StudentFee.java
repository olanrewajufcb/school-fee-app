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

@Data
@Builder
@Table(name = "student_fees", schema = "fee")
public class StudentFee {

  @Id
  private UUID id;

  @Column("student_id")
  private UUID studentId;

  @Column("school_id")
  private UUID schoolId;

  @Column("fee_structure_id")
  private UUID feeStructureId;

  @Column("total_amount")
  private BigDecimal totalAmount;

  @Column("discount_amount")
  private BigDecimal discountAmount;

  @Column("discount_reason")
  private String discountReason;

  @Column("due_date")
  private LocalDate dueDate;


  @Column("is_late_fee_applied")
  private Boolean isLateFeeApplied;

  @Column("late_fee_amount")
  private BigDecimal lateFeeAmount;

  @Column("last_reminder_sent_at")
  private Instant lastReminderSentAt;

  @Column("reminder_count")
  private Integer reminderCount;

  @Column("created_at")
  private Instant createdAt;

  @Column("updated_at")
  private Instant updatedAt;

  @Version
  private Long version;
}
