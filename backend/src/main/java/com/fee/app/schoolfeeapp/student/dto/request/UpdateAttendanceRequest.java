package com.fee.app.schoolfeeapp.student.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateAttendanceRequest(
        @NotBlank(message = "Status is required")
        String status,           // PRESENT, ABSENT, LATE, EXCUSED, PICKED_UP_EARLY

        String arrivalTime,      // HH:mm
        String broughtBy,
        String departureTime,    // HH:mm
        String pickedUpBy,
        String pickUpPersonName,
        String pickUpPersonPhone,
        String notes
) {}