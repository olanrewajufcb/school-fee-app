package com.fee.app.schoolfeeapp.notification.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.dto.request.NotificationTemplateResponse;
import com.fee.app.schoolfeeapp.notification.dto.request.SendBulkNotificationRequest;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import com.fee.app.schoolfeeapp.notification.dto.response.ReminderScheduleResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.SendBulkNotificationResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.UpdateTemplateResponse;
import com.fee.app.schoolfeeapp.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private static final UUID TEMPLATE_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");

    @Test
    @DisplayName("Should get templates successfully")
    void shouldGetTemplatesSuccessfully() {
        List<NotificationTemplateResponse> serviceResponse = List.of(templateResponse());
        when(notificationService.getTemplates("SMS")).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(notificationController.getTemplates("SMS"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<NotificationTemplateResponse>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(notificationService).getTemplates("SMS");
    }

    @Test
    @DisplayName("Should update template successfully")
    void shouldUpdateTemplateSuccessfully() {
        UpdateTemplateRequest request = new UpdateTemplateRequest("Body", "Name", true);
        UpdateTemplateResponse serviceResponse = new UpdateTemplateResponse(
                TEMPLATE_ID,
                Instant.parse("2026-06-05T10:15:30Z"));
        when(notificationService.updateTemplate(TEMPLATE_ID, request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(notificationController.updateTemplate(TEMPLATE_ID, request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isSuccess()).isTrue();
                    assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(notificationService).updateTemplate(TEMPLATE_ID, request);
    }

    @Test
    @DisplayName("Should propagate template update error")
    void shouldPropagateTemplateUpdateError() {
        UpdateTemplateRequest request = new UpdateTemplateRequest("Body", null, null);
        SchoolFeeException expectedError = new SchoolFeeException(
                "TEMPLATE_NOT_FOUND",
                "Template not found");
        when(notificationService.updateTemplate(TEMPLATE_ID, request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(notificationController.updateTemplate(TEMPLATE_ID, request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(notificationService).updateTemplate(TEMPLATE_ID, request);
    }

    @Test
    @DisplayName("Should get reminder schedules successfully")
    void shouldGetReminderSchedulesSuccessfully() {
        List<ReminderScheduleResponse> serviceResponse = List.of(
                new ReminderScheduleResponse(
                        UUID.randomUUID(), "Before Due", "BEFORE_DUE", 3,
                        LocalTime.of(9, 0), "FEE_REMINDER", true));
        when(notificationService.getReminderSchedules()).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(notificationController.getReminderSchedules())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<ReminderScheduleResponse>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().getFirst().name()).isEqualTo("Before Due");
                })
                .verifyComplete();

        verify(notificationService).getReminderSchedules();
    }

    @Test
    @DisplayName("Should propagate reminder schedules error")
    void shouldPropagateReminderSchedulesError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "SCHOOL_CONTEXT_REQUIRED",
                "A school context is required");
        when(notificationService.getReminderSchedules()).thenReturn(Mono.error(expectedError));

        StepVerifier.create(notificationController.getReminderSchedules())
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(notificationService).getReminderSchedules();
    }

    @Test
    @DisplayName("Should send bulk notifications successfully")
    void shouldSendBulkNotificationsSuccessfully() {
        UUID feeId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(feeId), "FEE_REMINDER", "SMS");
        SendBulkNotificationResponse serviceResponse = new SendBulkNotificationResponse(
                batchId,
                1,
                BigDecimal.valueOf(4),
                "QUEUED",
                "1 of 1 messages queued for delivery");
        when(notificationService.sendBulkNotifications(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(notificationController.sendBulk(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    ApiResponse<SendBulkNotificationResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(notificationService).sendBulkNotifications(request);
    }

    @Test
    @DisplayName("Should propagate bulk notification error")
    void shouldPropagateBulkNotificationError() {
        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(UUID.randomUUID()), "MISSING", "SMS");
        SchoolFeeException expectedError = new SchoolFeeException(
                "TEMPLATE_NOT_FOUND",
                "Active template not found");
        when(notificationService.sendBulkNotifications(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(notificationController.sendBulk(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(notificationService).sendBulkNotifications(request);
    }

    private NotificationTemplateResponse templateResponse() {
        return new NotificationTemplateResponse(
                TEMPLATE_ID,
                "FEE_REMINDER",
                "Fee Reminder",
                "SMS",
                "Hello {parent_name}, pay {amount}.",
                List.of("parent_name", "amount"),
                true,
                true,
                Instant.parse("2026-06-05T10:00:00Z"),
                Instant.parse("2026-06-05T10:00:00Z"));
    }
}
