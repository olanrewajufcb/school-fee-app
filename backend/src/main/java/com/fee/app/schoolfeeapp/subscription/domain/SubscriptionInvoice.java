package com.fee.app.schoolfeeapp.subscription.domain;

import com.fee.app.schoolfeeapp.subscription.enums.BillingCycle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("subscription.invoices")
public class SubscriptionInvoice {

    @Id
    private UUID id;

    @Column("subscription_id")
    private UUID subscriptionId;

    @Column("school_id")
    private UUID schoolId;

    @Column("invoice_number")
    private String invoiceNumber;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("student_count")
    private Integer studentCount;

    @Column("billing_cycle")
    private BillingCycle billingCycle;

    @Column("status")
    private String status; // PENDING, PAID, OVERDUE, CANCELLED

    @Column("due_date")
    private LocalDate dueDate;

    @Column("paid_at")
    private Instant paidAt;

    @Column("payment_reference")
    private String paymentReference;

    @Column("payment_channel")
    private String paymentChannel;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Integer version;
}
