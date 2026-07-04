package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.constraints.Size;

public record CommentRequest(
        @Size(max = 1000, message = "Comment cannot exceed 1000 characters")
        String comment,
        Boolean autoGenerate
) {
    public CommentRequest(String comment) {
        this(comment, false);
    }

    public boolean shouldAutoGenerate() {
        return Boolean.TRUE.equals(autoGenerate);
    }
}
