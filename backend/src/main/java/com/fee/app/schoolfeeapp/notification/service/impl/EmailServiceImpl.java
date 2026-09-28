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

    @Override
    public Mono<Void> sendGuardianInvitationEmail(String toEmail, String guardianName, String schoolName, String invitationLink) {
        return Mono.fromCallable(() -> {
            log.info("Sending guardian invitation email to: {} for school: {}", toEmail, schoolName);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String sender = fromEmail;
            if (sender == null || sender.isBlank()) {
                sender = "noreply@schoolfee.app";
            }
            helper.setFrom(sender);
            helper.setTo(toEmail);
            helper.setSubject("Invitation to Join " + schoolName + " Parent Portal");

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b; }
                        .card { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 32px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }
                        .header { text-align: center; margin-bottom: 24px; }
                        .header h1 { font-size: 22px; color: #0f172a; margin: 8px 0 0 0; }
                        .school-badge { display: inline-block; background-color: #eff6ff; color: #2563eb; font-size: 13px; font-weight: 600; padding: 4px 12px; border-radius: 20px; margin-bottom: 8px; }
                        .content { font-size: 15px; line-height: 1.6; color: #334155; }
                        .feature-list { background-color: #f8fafc; border-radius: 8px; padding: 16px 20px; margin: 20px 0; }
                        .feature-list ul { margin: 0; padding-left: 18px; }
                        .feature-list li { margin-bottom: 6px; font-size: 14px; }
                        .btn-container { text-align: center; margin: 28px 0; }
                        .btn { background-color: #2563eb; color: #ffffff !important; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: 600; font-size: 15px; display: inline-block; }
                        .link-fallback { font-size: 12px; color: #64748b; word-break: break-all; margin-top: 16px; }
                        .footer { margin-top: 32px; padding-top: 20px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #94a3b8; text-align: center; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="header">
                            <span class="school-badge">%s</span>
                            <h1>Parent Portal Invitation</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>You have been invited by <strong>%s</strong> to set up your account on the <strong>SchoolFee Parent Portal</strong>.</p>
                            
                            <div class="feature-list">
                                <p style="margin: 0 0 8px 0; font-weight: 600; color: #0f172a;">With your parent account, you can:</p>
                                <ul>
                                    <li>Pay school fees seamlessly online and obtain instant receipts</li>
                                    <li>Access terminal exam results & academic report cards</li>
                                    <li>Monitor daily attendance and punctuality in real-time</li>
                                    <li>Receive important school broadcast announcements</li>
                                </ul>
                            </div>

                            <div class="btn-container">
                                <a href="%s" class="btn">Set Up Parent Account</a>
                            </div>

                            <p class="link-fallback">
                                If the button above does not work, copy and paste this link into your browser:<br/>
                                <a href="%s" style="color: #2563eb;">%s</a>
                            </p>
                        </div>
                        <div class="footer">
                            <p>&copy; %s. Powered by SchoolFee.</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                schoolName,
                guardianName != null && !guardianName.isBlank() ? guardianName : "Parent/Guardian",
                schoolName,
                invitationLink,
                invitationLink,
                invitationLink,
                schoolName
            );

            helper.setText(htmlContent, true);
            javaMailSender.send(message);

            log.info("Guardian invitation email sent successfully to: {}", toEmail);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}

