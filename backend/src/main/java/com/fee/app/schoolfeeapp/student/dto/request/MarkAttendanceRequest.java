package com.fee.app.schoolfeeapp.student.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record MarkAttendanceRequest(
        @NotEmpty(message = "At least one attendance record is required")
        @Valid
        List<AttendanceMark> marks
) {
    public record AttendanceMark(
            java.util.UUID studentId,
            String status,           // PRESENT, ABSENT, LATE, EXCUSED
            String arrivalTime,      // HH:mm (morning only)
            String broughtBy,        // morning only
            String departureTime,    // HH:mm (afternoon only)
            String pickedUpBy,       // afternoon only
            String pickUpPersonName,  // afternoon only
            String pickUpPersonPhone, // afternoon only
            String notes
    ) {}
}