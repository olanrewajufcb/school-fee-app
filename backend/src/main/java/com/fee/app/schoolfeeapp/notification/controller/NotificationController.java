package com.fee.app.schoolfeeapp.notification.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.notification.dto.request.NotificationTemplateResponse;
import com.fee.app.schoolfeeapp.notification.dto.request.SendBulkNotificationRequest;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import com.fee.app.schoolfeeapp.notification.dto.response.NotificationBalanceResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.ReminderScheduleResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.SendBulkNotificationResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.UpdateTemplateResponse;
import com.fee.app.schoolfeeapp.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<List<NotificationTemplateResponse>>>> getTemplates(
            @RequestParam(required = false) String channel) {
        return notificationService.getTemplates(channel)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PutMapping("/templates/{templateId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<UpdateTemplateResponse>>> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateTemplateRequest request) {
        return notificationService.updateTemplate(templateId, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @GetMapping("/reminder-schedules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<List<ReminderScheduleResponse>>>> getReminderSchedules() {
        return notificationService.getReminderSchedules()
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PostMapping("/send-bulk")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<SendBulkNotificationResponse>>> sendBulk(
            @Valid @RequestBody SendBulkNotificationRequest request) {
        return notificationService.sendBulkNotifications(request)
                .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.success(response)));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<NotificationBalanceResponse>>> getBalance() {
        return notificationService.getBalance()
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }
}