package com.fee.app.schoolfeeapp.subscription.service.impl;

import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.subscription.domain.SchoolSubscription;
import com.fee.app.schoolfeeapp.subscription.domain.SubscriptionInvoice;
import com.fee.app.schoolfeeapp.subscription.domain.SubscriptionPlan;
import com.fee.app.schoolfeeapp.subscription.dto.request.CalculatePriceRequest;
import com.fee.app.schoolfeeapp.subscription.dto.request.UpgradeSubscriptionRequest;
import com.fee.app.schoolfeeapp.subscription.dto.response.PriceCalculationResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SchoolSubscriptionResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SubscriptionInvoiceResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SubscriptionPlanResponse;
import com.fee.app.schoolfeeapp.subscription.enums.BillingCycle;
import com.fee.app.schoolfeeapp.subscription.enums.PlanCode;
import com.fee.app.schoolfeeapp.subscription.enums.SubscriptionStatus;
import com.fee.app.schoolfeeapp.subscription.repository.SchoolSubscriptionRepository;
import com.fee.app.schoolfeeapp.subscription.repository.SubscriptionInvoiceRepository;
import com.fee.app.schoolfeeapp.subscription.repository.SubscriptionPlanRepository;
import com.fee.app.schoolfeeapp.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SchoolSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final StudentRepository studentRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<SchoolSubscriptionResponse> initializeTrialSubscription(UUID schoolId) {
        return subscriptionRepository.findBySchoolId(schoolId)
                .flatMap(existing -> planRepository.findById(existing.getPlanId())
                        .map(plan -> toResponse(existing, plan)))
                .switchIfEmpty(
                        planRepository.findByCode(PlanCode.FULL_SUITE)
                                .switchIfEmpty(Mono.error(new SchoolFeeException("PLAN_NOT_FOUND", "Default Full Suite plan not configured")))
                                .flatMap(fullSuitePlan -> {
                                    Instant now = Instant.now();
                                    Instant trialEnd = now.plus(30, ChronoUnit.DAYS);
                                    Instant graceEnd = trialEnd.plus(7, ChronoUnit.DAYS);

                                    SchoolSubscription subscription = SchoolSubscription.builder()
                                            .schoolId(schoolId)
                                            .planId(fullSuitePlan.getId())
                                            .billingCycle(BillingCycle.TERMLY)
                                            .status(SubscriptionStatus.TRIAL)
                                            .studentCount(0)
                                            .trialStartDate(now)
                                            .trialEndDate(trialEnd)
                                            .gracePeriodEndDate(graceEnd)
                                            .amountDue(BigDecimal.ZERO)
                                            .autoRenew(true)
                                            .createdAt(now)
                                            .updatedAt(now)
                                            .build();

                                    return subscriptionRepository.save(subscription)
                                            .map(saved -> toResponse(saved, fullSuitePlan));
                                })
                );
    }

    @Override
    public Mono<SchoolSubscriptionResponse> getSchoolSubscription(UUID schoolId) {
        return subscriptionRepository.findBySchoolId(schoolId)
                .switchIfEmpty(initializeTrialSubscription(schoolId).flatMap(res -> subscriptionRepository.findById(res.id())))
                .flatMap(subscription -> studentRepository.countBySchoolIdAndDeletedAtIsNull(schoolId)
                        .defaultIfEmpty(0L)
                        .flatMap(count -> {
                            subscription.setStudentCount(count.intValue());
                            // Check if trial has expired
                            if (subscription.getStatus() == SubscriptionStatus.TRIAL &&
                                    subscription.getTrialEndDate() != null &&
                                    Instant.now().isAfter(subscription.getTrialEndDate())) {
                                subscription.setStatus(SubscriptionStatus.EXPIRED);
                            }
                            return subscriptionRepository.save(subscription);
                        })
                        .flatMap(savedSub -> planRepository.findById(savedSub.getPlanId())
                                .map(plan -> toResponse(savedSub, plan))));
    }

    @Override
    public Flux<SubscriptionPlanResponse> getAllPlans() {
        return planRepository.findAllActivePlans()
                .map(this::toPlanResponse);
    }

    @Override
    public Mono<PriceCalculationResponse> calculatePrice(CalculatePriceRequest request) {
        return planRepository.findByCode(request.planCode())
                .switchIfEmpty(Mono.error(new SchoolFeeException("PLAN_NOT_FOUND", "Plan not found: " + request.planCode())))
                .map(plan -> {
                    BigDecimal rate = request.billingCycle() == BillingCycle.TERMLY
                            ? plan.getPricePerStudentTermly()
                            : plan.getPricePerStudentAnnually();

                    BigDecimal total = rate.multiply(BigDecimal.valueOf(request.studentCount()));

                    // Calculate annual savings if billing annually (3 terms vs 1 annual payment)
                    BigDecimal savings = BigDecimal.ZERO;
                    if (request.billingCycle() == BillingCycle.ANNUALLY) {
                        BigDecimal termlyTotalForYear = plan.getPricePerStudentTermly()
                                .multiply(BigDecimal.valueOf(3))
                                .multiply(BigDecimal.valueOf(request.studentCount()));
                        savings = termlyTotalForYear.subtract(total).max(BigDecimal.ZERO);
                    }

                    return PriceCalculationResponse.builder()
                            .planCode(plan.getCode())
                            .planName(plan.getName())
                            .billingCycle(request.billingCycle())
                            .studentCount(request.studentCount())
                            .ratePerStudent(rate)
                            .totalAmount(total)
                            .currency(plan.getCurrency())
                            .savingsAmount(savings)
                            .build();
                });
    }

    @Override
    public Mono<SchoolSubscriptionResponse> upgradeOrChangePlan(UUID schoolId, UpgradeSubscriptionRequest request) {
        return planRepository.findByCode(request.planCode())
                .switchIfEmpty(Mono.error(new SchoolFeeException("PLAN_NOT_FOUND", "Plan not found: " + request.planCode())))
                .flatMap(plan -> subscriptionRepository.findBySchoolId(schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException("SUBSCRIPTION_NOT_FOUND", "Subscription not found for school: " + schoolId)))
                        .flatMap(subscription -> studentRepository.countBySchoolIdAndDeletedAtIsNull(schoolId)
                                .defaultIfEmpty(0L)
                                .flatMap(studentCount -> {
                                    int count = Math.max(1, studentCount.intValue());
                                    BigDecimal rate = request.billingCycle() == BillingCycle.TERMLY
                                            ? plan.getPricePerStudentTermly()
                                            : plan.getPricePerStudentAnnually();
                                    BigDecimal amount = rate.multiply(BigDecimal.valueOf(count));

                                    Instant now = Instant.now();
                                    subscription.setPlanId(plan.getId());
                                    subscription.setBillingCycle(request.billingCycle());
                                    subscription.setStudentCount(count);
                                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                                    subscription.setCurrentPeriodStart(now);

                                    // Next period end
                                    Instant periodEnd = request.billingCycle() == BillingCycle.TERMLY
                                            ? now.plus(120, ChronoUnit.DAYS) // ~ 1 academic term
                                            : now.plus(365, ChronoUnit.DAYS); // 1 academic year
                                    subscription.setCurrentPeriodEnd(periodEnd);
                                    subscription.setAmountDue(amount);
                                    subscription.setUpdatedAt(now);

                                    // Create invoice for this subscription activation
                                    String invoiceNumber = "INV-SUB-" + System.currentTimeMillis() % 1000000;
                                    SubscriptionInvoice invoice = SubscriptionInvoice.builder()
                                            .subscriptionId(subscription.getId())
                                            .schoolId(schoolId)
                                            .invoiceNumber(invoiceNumber)
                                            .amount(amount)
                                            .currency(plan.getCurrency())
                                            .studentCount(count)
                                            .billingCycle(request.billingCycle())
                                            .status("PENDING")
                                            .dueDate(LocalDate.now().plusDays(14))
                                            .createdAt(now)
                                            .updatedAt(now)
                                            .build();

                                    Mono<SchoolSubscriptionResponse> work = subscriptionRepository.save(subscription)
                                            .flatMap(savedSub -> invoiceRepository.save(invoice).thenReturn(savedSub))
                                            .map(savedSub -> toResponse(savedSub, plan));

                                    return transactionalOperator.transactional(work);
                                })));
    }

    @Override
    public Flux<SubscriptionInvoiceResponse> getSchoolInvoices(UUID schoolId) {
        return invoiceRepository.findBySchoolIdOrderByCreatedAtDesc(schoolId)
                .map(this::toInvoiceResponse);
    }

    private SchoolSubscriptionResponse toResponse(SchoolSubscription sub, SubscriptionPlan plan) {
        Long daysLeft = null;
        if (sub.getTrialEndDate() != null) {
            long days = ChronoUnit.DAYS.between(Instant.now(), sub.getTrialEndDate());
            daysLeft = Math.max(0, days);
        }

        return SchoolSubscriptionResponse.builder()
                .id(sub.getId())
                .schoolId(sub.getSchoolId())
                .plan(toPlanResponse(plan))
                .billingCycle(sub.getBillingCycle())
                .status(sub.getStatus())
                .studentCount(sub.getStudentCount())
                .trialStartDate(sub.getTrialStartDate())
                .trialEndDate(sub.getTrialEndDate())
                .daysRemainingInTrial(daysLeft)
                .currentPeriodStart(sub.getCurrentPeriodStart())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .gracePeriodEndDate(sub.getGracePeriodEndDate())
                .amountDue(sub.getAmountDue())
                .autoRenew(sub.getAutoRenew())
                .build();
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        return SubscriptionPlanResponse.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .pricePerStudentTermly(plan.getPricePerStudentTermly())
                .pricePerStudentAnnually(plan.getPricePerStudentAnnually())
                .currency(plan.getCurrency())
                .hasOnlinePayments(plan.getHasOnlinePayments())
                .hasResultsManagement(plan.getHasResultsManagement())
                .hasAttendanceTracking(plan.getHasAttendanceTracking())
                .hasAutomatedNotifications(plan.getHasAutomatedNotifications())
                .build();
    }

    private SubscriptionInvoiceResponse toInvoiceResponse(SubscriptionInvoice inv) {
        return SubscriptionInvoiceResponse.builder()
                .id(inv.getId())
                .subscriptionId(inv.getSubscriptionId())
                .schoolId(inv.getSchoolId())
                .invoiceNumber(inv.getInvoiceNumber())
                .amount(inv.getAmount())
                .currency(inv.getCurrency())
                .studentCount(inv.getStudentCount())
                .billingCycle(inv.getBillingCycle())
                .status(inv.getStatus())
                .dueDate(inv.getDueDate())
                .paidAt(inv.getPaidAt())
                .paymentReference(inv.getPaymentReference())
                .createdAt(inv.getCreatedAt())
                .build();
    }
}
