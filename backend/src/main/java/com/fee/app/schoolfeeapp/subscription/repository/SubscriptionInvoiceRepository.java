package com.fee.app.schoolfeeapp.subscription.repository;

import com.fee.app.schoolfeeapp.subscription.domain.SubscriptionInvoice;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SubscriptionInvoiceRepository extends ReactiveCrudRepository<SubscriptionInvoice, UUID> {

    Flux<SubscriptionInvoice> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);

    Mono<SubscriptionInvoice> findByInvoiceNumber(String invoiceNumber);

    @Query("SELECT * FROM subscription.invoices WHERE subscription_id = :subscriptionId ORDER BY created_at DESC")
    Flux<SubscriptionInvoice> findBySubscriptionId(UUID subscriptionId);
}
