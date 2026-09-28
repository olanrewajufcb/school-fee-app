package com.fee.app.schoolfeeapp.subscription.repository;

import com.fee.app.schoolfeeapp.subscription.domain.SubscriptionPlan;
import com.fee.app.schoolfeeapp.subscription.enums.PlanCode;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SubscriptionPlanRepository extends ReactiveCrudRepository<SubscriptionPlan, UUID> {

    Mono<SubscriptionPlan> findByCode(PlanCode code);

    @Query("SELECT * FROM subscription.plans WHERE is_active = true ORDER BY price_per_student_termly ASC")
    Flux<SubscriptionPlan> findAllActivePlans();
}
