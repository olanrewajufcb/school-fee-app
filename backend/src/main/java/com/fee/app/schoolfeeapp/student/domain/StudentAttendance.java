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
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student_attendance", schema = "attendance")
public class StudentAttendance {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    @Column("session_id")
    private UUID sessionId;

    @Column("student_id")
    private UUID studentId;

    @Column("class_id")
    private UUID classId;

    @Column("term_id")
    private UUID termId;

    private LocalDate date;

    private String status;           // PRESENT, ABSENT, LATE, EXCUSED, PICKED_UP_EARLY

    @Column("arrival_time")
    private LocalTime arrivalTime;

    @Column("brought_by")
    private String broughtBy;

    @Column("departure_time")
    private LocalTime departureTime;

    @Column("picked_up_by")
    private String pickedUpBy;

    @Column("pick_up_person_name")
    private String pickUpPersonName;

    @Column("pick_up_person_phone")
    private String pickUpPersonPhone;

    private String notes;

    @Column("marked_by")
    private UUID markedBy;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}