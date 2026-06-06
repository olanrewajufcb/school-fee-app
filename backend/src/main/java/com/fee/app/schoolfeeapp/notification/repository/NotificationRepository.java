package com.fee.app.schoolfeeapp.notification.repository;

import com.fee.app.schoolfeeapp.notification.domain.Notification;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, UUID> {

    @Query("""
            SELECT *
            FROM notification.notifications
            WHERE school_id = :schoolId
              AND context_type = :triggerType
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            """)
    Flux<Notification> findBySchoolIdAndTriggerTypeOrderByCreatedAtDesc(UUID schoolId, String triggerType);

    @Query("""
            SELECT *
            FROM notification.notifications
            WHERE context_type = :contextType
              AND context_id = :contextId
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            """)
    Flux<Notification> findByContextTypeAndContextId(String contextType, UUID contextId);
}
