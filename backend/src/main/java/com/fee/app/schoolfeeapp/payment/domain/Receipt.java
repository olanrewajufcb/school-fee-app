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
@Table("payment.receipts")
public class Receipt {

  @Id
  private UUID id;

  @Column("payment_id")
  private UUID paymentId;

  @Column("receipt_number")
  private String receiptNumber;

  @Column("student_id")
  private UUID studentId;

  @Column("school_id")
  private UUID schoolId;

  private BigDecimal amount;

  @Column("receipt_generated_at")
  private Instant receiptGeneratedAt;

  @Column("amount_in_words")
  private String amountInWords;

  @Column("payment_date")
  private Instant paymentDate;

  @Column("payment_method")
  private String paymentMethod;

  @Column("paid_by")
  private UUID paidBy;

  @Column("paid_by_name")
  private String paidByName;

  private JsonNode breakdown;

  @Column("fee_description")
  private String feeDescription;

  @Column("generated_by")
  private UUID generatedBy;

  @Column("pdf_url")
  private String pdfUrl;

  @Column("sms_sent")
  private Boolean smsSent;

  @Column("email_sent")
  private Boolean emailSent;

  @Column("created_at")
  private Instant createdAt;

  @Column("updated_at")
  private Instant updatedAt;

  @Column("deleted_at")
  private Instant deletedAt;

  @Version
  private Integer  version;

}
