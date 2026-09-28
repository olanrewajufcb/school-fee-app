package com.fee.app.schoolfeeapp.subscription.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.subscription.dto.request.CalculatePriceRequest;
import com.fee.app.schoolfeeapp.subscription.dto.request.UpgradeSubscriptionRequest;
import com.fee.app.schoolfeeapp.subscription.dto.response.PriceCalculationResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SchoolSubscriptionResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SubscriptionInvoiceResponse;
import com.fee.app.schoolfeeapp.subscription.dto.response.SubscriptionPlanResponse;
import com.fee.app.schoolfeeapp.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * GET /api/v1/subscriptions/plans
     * Public / Authenticated: List all active plans.
     */
    @GetMapping("/plans")
    public Mono<ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>>> getPlans() {
        return subscriptionService.getAllPlans()
                .collectList()
                .map(plans -> ResponseEntity.ok(ApiResponse.success(plans)));
    }

    /**
     * POST /api/v1/subscriptions/calculate-price
     * Public / Authenticated: Interactive pricing calculator for given student count & billing cycle.
     */
    @PostMapping("/calculate-price")
    public Mono<ResponseEntity<ApiResponse<PriceCalculationResponse>>> calculatePrice(
            @Valid @RequestBody CalculatePriceRequest request) {
        return subscriptionService.calculatePrice(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/subscriptions/schools/{schoolId}
     * Get the active subscription details and trial status for a school.
     */
    @GetMapping("/schools/{schoolId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SchoolSubscriptionResponse>>> getSchoolSubscription(
            @PathVariable UUID schoolId) {
        return subscriptionService.getSchoolSubscription(schoolId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * POST /api/v1/subscriptions/schools/{schoolId}/upgrade
     * Upgrade, activate, or change subscription plan.
     */
    @PostMapping("/schools/{schoolId}/upgrade")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SchoolSubscriptionResponse>>> upgradePlan(
            @PathVariable UUID schoolId,
            @Valid @RequestBody UpgradeSubscriptionRequest request) {
        return subscriptionService.upgradeOrChangePlan(schoolId, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/subscriptions/schools/{schoolId}/invoices
     * Get invoice history for school subscriptions.
     */
    @GetMapping("/schools/{schoolId}/invoices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<SubscriptionInvoiceResponse>>>> getSchoolInvoices(
            @PathVariable UUID schoolId) {
        return subscriptionService.getSchoolInvoices(schoolId)
                .collectList()
                .map(invoices -> ResponseEntity.ok(ApiResponse.success(invoices)));
    }
}
