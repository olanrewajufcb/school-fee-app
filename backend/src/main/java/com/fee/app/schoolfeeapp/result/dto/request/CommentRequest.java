package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(
        @NotBlank(message = "Comment is required")
        String comment
) {}