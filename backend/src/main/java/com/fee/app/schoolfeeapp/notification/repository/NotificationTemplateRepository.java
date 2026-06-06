package com.fee.app.schoolfeeapp.notification.repository;

import com.fee.app.schoolfeeapp.notification.domain.NotificationTemplate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends ReactiveCrudRepository<NotificationTemplate, UUID> {

    @Query("""
            SELECT *
            FROM notification.notification_templates
            WHERE school_id = :schoolId
              AND deleted_at IS NULL
            ORDER BY channel ASC, template_code ASC
            """)
    Flux<NotificationTemplate> findBySchoolId(UUID schoolId);

    @Query("""
            SELECT *
            FROM notification.notification_templates
            WHERE school_id = :schoolId
              AND channel = :channel
              AND deleted_at IS NULL
            ORDER BY template_code ASC
            """)
    Flux<NotificationTemplate> findBySchoolIdAndChannel(UUID schoolId, String channel);

    @Query("""
            SELECT *
            FROM notification.notification_templates
            WHERE id = :id
              AND school_id = :schoolId
              AND deleted_at IS NULL
            """)
    Mono<NotificationTemplate> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Query("""
            SELECT *
            FROM notification.notification_templates
            WHERE school_id = :schoolId
              AND template_code = :templateCode
              AND deleted_at IS NULL
            ORDER BY channel ASC
            LIMIT 1
            """)
    Mono<NotificationTemplate> findBySchoolIdAndTemplateCode(UUID schoolId, String templateCode);

    @Query("""
            SELECT EXISTS (
                SELECT 1
                FROM notification.notification_templates
                WHERE school_id = :schoolId
                  AND template_code = :templateCode
                  AND channel = :channel
                  AND deleted_at IS NULL
            )
            """)
    Mono<Boolean> existsBySchoolIdAndTemplateCodeAndChannel(
            UUID schoolId, String templateCode, String channel);
}
