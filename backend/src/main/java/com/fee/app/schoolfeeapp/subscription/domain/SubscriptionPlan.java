package com.fee.app.schoolfeeapp.subscription.domain;

import com.fee.app.schoolfeeapp.subscription.enums.PlanCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("subscription.plans")
public class SubscriptionPlan {

    @Id
    private UUID id;

    @Column("code")
    private PlanCode code;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("price_per_student_termly")
    private BigDecimal pricePerStudentTermly;

    @Column("price_per_student_annually")
    private BigDecimal pricePerStudentAnnually;

    @Column("currency")
    private String currency;

    @Column("has_online_payments")
    private Boolean hasOnlinePayments;

    @Column("has_results_management")
    private Boolean hasResultsManagement;

    @Column("has_attendance_tracking")
    private Boolean hasAttendanceTracking;

    @Column("has_automated_notifications")
    private Boolean hasAutomatedNotifications;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private Instant createdAt;
}
