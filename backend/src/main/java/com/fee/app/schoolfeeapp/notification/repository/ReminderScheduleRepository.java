package com.fee.app.schoolfeeapp.notification.repository;

import com.fee.app.schoolfeeapp.notification.domain.ReminderSchedule;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ReminderScheduleRepository extends ReactiveCrudRepository<ReminderSchedule, UUID> {

    Flux<ReminderSchedule> findBySchoolId(UUID schoolId);
    Flux<ReminderSchedule> findBySchoolIdAndIsActiveTrue(UUID schoolId);
}