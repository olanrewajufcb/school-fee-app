package com.fee.app.schoolfeeapp.student.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "attendance_sessions", schema = "attendance")
public class AttendanceSession {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    @Column("class_id")
    private UUID classId;

    @Column("term_id")
    private UUID termId;

    private LocalDate date;

    @Column("session_type")
    private String sessionType;     // MORNING_ARRIVAL, AFTERNOON_DEPARTURE

    @Column("marked_by")
    private UUID markedBy;

    @Column("marked_at")
    private Instant markedAt;

    @Column("is_complete")
    private boolean isComplete;

    @Column("created_at")
    private Instant createdAt;
}