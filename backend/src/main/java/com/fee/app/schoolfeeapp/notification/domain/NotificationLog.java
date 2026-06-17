package com.fee.app.schoolfeeapp.notification.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table("notification.notification_logs")
public class NotificationLog {

  @Id
  private UUID id;

  @Column("school_id")
  private UUID schoolId;

  private LocalDate date;

  private String channel;

  @Column("total_sent")
  private Integer totalSent;

  @Column("total_delivered")
  private Integer totalDelivered;

  @Column("total_failed")
  private Integer totalFailed;

  @Column("total_cost")
  private BigDecimal totalCost;

  @Column("created_at")
  private Instant createdAt;
}