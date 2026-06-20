package com.fee.app.schoolfeeapp.notification.service;

import reactor.core.publisher.Mono;

public interface EmailService {
    Mono<Void> sendAdminWelcomeEmail(String toEmail, String schoolName, String temporaryPassword);
    Mono<Void> sendStaffWelcomeEmail(String toEmail, String schoolName, String temporaryPassword);
}
