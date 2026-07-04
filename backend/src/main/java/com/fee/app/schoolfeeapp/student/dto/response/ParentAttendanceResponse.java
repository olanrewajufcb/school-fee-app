package com.fee.app.schoolfeeapp.student.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ParentAttendanceResponse(
        UUID studentId,
        String studentName,
        String className,
        LocalDate date,
        LocalTime arrivalTime,
        String broughtBy,
        String arrivalStatus,
        LocalTime departureTime,
        String departureStatus,
        String pickedUpBy,
        String pickUpPersonName,
        String pickUpPersonPhone
) {}
