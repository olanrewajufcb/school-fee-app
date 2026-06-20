package com.fee.app.schoolfeeapp.student.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AttendanceResponse(
        UUID attendanceId,
        UUID studentId,
        String studentName,
        String admissionNumber,
        String status,
        LocalDate date,
        String sessionType,
        LocalTime arrivalTime,
        String broughtBy,
        LocalTime departureTime,
        String pickedUpBy,
        String pickUpPersonName,
        String pickUpPersonPhone,
        String notes
) {}