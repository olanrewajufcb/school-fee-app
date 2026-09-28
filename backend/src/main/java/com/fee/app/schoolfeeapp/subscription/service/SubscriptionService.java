package com.fee.app.schoolfeeapp.subscription.service;

import com.fee.app.schoolfeeapp.subscription.dto.request.CalculatePriceRequest;
import com.fee.app.schoolfeeapp.subscription.dto.request.UpgradeSubscriptionRequest;
import com.fee.app.schoolfeeapp.subscription.dto.response.PriceCalculationResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SchoolSubscriptionResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SubscriptionInvoiceResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SubscriptionPlanResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SubscriptionService {

    /**
     * Initializes a 30-day free trial with full platform access for a newly created school.
     */
    Mono<SchoolSubscriptionResponse> initializeTrialSubscription(UUID schoolId);

    /**
     * Retrieves the current subscription details for a school, including trial days remaining.
     */
    Mono<SchoolSubscriptionResponse> getSchoolSubscription(UUID schoolId);

    /**
     * Retrieves all available subscription plans.
     */
    Flux<SubscriptionPlanResponse> getAllPlans();

    /**
     * Calculates the price for a given student count, plan, and billing cycle.
     */
    Mono<PriceCalculationResponse> calculatePrice(CalculatePriceRequest request);

    /**
     * Upgrades or changes the active subscription plan and billing cycle for a school.
     */
    Mono<SchoolSubscriptionResponse> upgradeOrChangePlan(UUID schoolId, UpgradeSubscriptionRequest request);

    /**
     * Retrieves invoices/receipts for a school's subscriptions.
     */
    Flux<SubscriptionInvoiceResponse> getSchoolInvoices(UUID schoolId);
}
