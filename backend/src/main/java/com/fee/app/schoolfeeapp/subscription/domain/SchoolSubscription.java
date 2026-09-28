package com.fee.app.schoolfeeapp.subscription.domain;

import com.fee.app.schoolfeeapp.subscription.enums.BillingCycle;
import com.fee.app.schoolfeeapp.subscription.enums.SubscriptionStatus;
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
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("subscription.school_subscriptions")
public class SchoolSubscription {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    @Column("plan_id")
    private UUID planId;

    @Column("billing_cycle")
    private BillingCycle billingCycle;

    @Column("status")
    private SubscriptionStatus status;

    @Column("student_count")
    private Integer studentCount;

    @Column("trial_start_date")
    private Instant trialStartDate;

    @Column("trial_end_date")
    private Instant trialEndDate;

    @Column("current_period_start")
    private Instant currentPeriodStart;

    @Column("current_period_end")
    private Instant currentPeriodEnd;

    @Column("grace_period_end_date")
    private Instant gracePeriodEndDate;

    @Column("amount_due")
    private BigDecimal amountDue;

    @Column("auto_renew")
    private Boolean autoRenew;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Integer version;
}
