package com.fee.app.schoolfeeapp.notification.service;

import com.fee.app.schoolfeeapp.notification.dto.request.NotificationTemplateResponse;
import com.fee.app.schoolfeeapp.notification.dto.request.SendBulkNotificationRequest;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import com.fee.app.schoolfeeapp.notification.dto.response.NotificationBalanceResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.ReminderScheduleResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.SendBulkNotificationResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.UpdateTemplateResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Notification management service.
 * 
 * Handles:
 * - Template CRUD (get, update)
 * - Reminder schedule retrieval
 * - Bulk notification sending (SMS, WhatsApp, or both)
 * - Provider balance queries
 */
public interface NotificationService {

    /**
     * Get notification templates for the current school.
     * Optionally filtered by channel (SMS, WHATSAPP).
     */
    Mono<List<NotificationTemplateResponse>> getTemplates(String channel);

    /**
     * Update a notification template.
     */
    Mono<UpdateTemplateResponse> updateTemplate(UUID templateId, UpdateTemplateRequest request);

    /**
     * Get reminder schedules for the current school.
     */
    Mono<List<ReminderScheduleResponse>> getReminderSchedules();

    /**
     * Send bulk notifications to parents.
     * Supports SMS, WHATSAPP, or BOTH channels.
     */
    Mono<SendBulkNotificationResponse> sendBulkNotifications(SendBulkNotificationRequest request);

    /**
     * Get notification provider balance.
     */
    Mono<NotificationBalanceResponse> getBalance();
}