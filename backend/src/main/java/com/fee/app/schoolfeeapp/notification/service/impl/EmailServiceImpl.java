package com.fee.app.schoolfeeapp.notification.service.impl;

import com.fee.app.schoolfeeapp.notification.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username:noreply@schoolfee.app}")
    private String fromEmail;

    @Override
    public Mono<Void> sendAdminWelcomeEmail(String toEmail, String schoolName, String temporaryPassword) {
        return Mono.fromCallable(() -> {
            log.info("Sending welcome email to admin: {}", toEmail);
            
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            String sender = fromEmail;
            if (sender == null || sender.isBlank()) {
                sender = "noreply@schoolfee.app";
            }
            helper.setFrom(sender);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to SchoolFee App - " + schoolName);
            
            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <h2>Welcome to SchoolFee App!</h2>
                    <p>Hello,</p>
                    <p>Your administrator account for <strong>%s</strong> has been successfully created.</p>
                    <p>You can log in to the portal using the following credentials:</p>
                    <ul>
                        <li><strong>Email:</strong> %s</li>
                        <li><strong>Temporary Password:</strong> %s</li>
                    </ul>
                    <p><em>Note: You will be required to change this temporary password upon your first login.</em></p>
                    <br/>
                    <p>Best regards,<br/>The SchoolFee Team</p>
                </body>
                </html>
                """, schoolName, toEmail, temporaryPassword);
                
            helper.setText(htmlContent, true);
            javaMailSender.send(message);
            
            log.info("Welcome email sent successfully to: {}", toEmail);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> sendStaffWelcomeEmail(String toEmail, String schoolName, String temporaryPassword) {
        return Mono.fromCallable(() -> {
            log.info("Sending welcome email to staff: {}", toEmail);
            
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            String sender = fromEmail;
            if (sender == null || sender.isBlank()) {
                sender = "noreply@schoolfee.app";
            }
            helper.setFrom(sender);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to SchoolFee App - " + schoolName);
            
            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <h2>Welcome to SchoolFee App!</h2>
                    <p>Hello,</p>
                    <p>Your staff account for <strong>%s</strong> has been successfully created.</p>
                    <p>You can log in to the portal using the following credentials:</p>
                    <ul>
                        <li><strong>Email:</strong> %s</li>
                        <li><strong>Temporary Password:</strong> %s</li>
                    </ul>
                    <p><em>Note: You will be required to change this temporary password upon your first login.</em></p>
                    <br/>
                    <p>Best regards,<br/>The SchoolFee Team</p>
                </body>
                </html>
                """, schoolName, toEmail, temporaryPassword);
                
            helper.setText(htmlContent, true);
            javaMailSender.send(message);
            
            log.info("Staff welcome email sent successfully to: {}", toEmail);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> sendAttendanceNotificationEmail(String toEmail, String schoolName, String msg) {
        return Mono.fromCallable(() -> {
            log.info("Sending attendance notification email to parent: {}", toEmail);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String sender = fromEmail;
            if (sender == null || sender.isBlank()) {
                sender = "noreply@schoolfee.app";
            }
            helper.setFrom(sender);
            helper.setTo(toEmail);
            helper.setSubject("Student attendance notification email - " + schoolName);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <h2>Welcome to SchoolFee and Parent Management App! %s</h2>
                    <p>Hello,</p>
                    <p>%s</p>                   
                    <p>Best regards,<br/>The SchoolFee Team</p>
                </body>
                </html>
                """, schoolName, msg);

            helper.setText(htmlContent, true);
            javaMailSender.send(message);

            log.info("attendance notification email sent successfully to: {}", toEmail);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

}
