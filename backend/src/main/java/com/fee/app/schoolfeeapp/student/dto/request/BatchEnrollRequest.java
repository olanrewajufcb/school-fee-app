package com.fee.app.schoolfeeapp.student.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchEnrollRequest(
        @NotEmpty(message = "At least one student is required")
        @Size(max = 100, message = "Maximum 100 students per batch")
        List<@Valid EnrollStudentRequest> students
) {}