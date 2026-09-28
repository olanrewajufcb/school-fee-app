package com.fee.app.schoolfeeapp.subscription.repository;

import com.fee.app.schoolfeeapp.subscription.domain.SchoolSubscription;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SchoolSubscriptionRepository extends ReactiveCrudRepository<SchoolSubscription, UUID> {

    Mono<SchoolSubscription> findBySchoolId(UUID schoolId);

    @Query("SELECT * FROM subscription.school_subscriptions WHERE status = :status")
    Flux<SchoolSubscription> findByStatus(String status);
}
