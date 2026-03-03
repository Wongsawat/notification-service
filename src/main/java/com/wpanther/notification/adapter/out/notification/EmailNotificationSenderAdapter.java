package com.wpanther.notification.adapter.out.notification;

import com.wpanther.notification.application.port.out.NotificationSenderPort;
import com.wpanther.notification.domain.exception.NotificationException;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email notification sender adapter using Spring Mail
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationSenderAdapter implements NotificationSenderPort {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Override
    public void send(Notification notification) throws NotificationException {
        try {
            log.info("Sending email notification: id={}, recipient={}, subject={}",
                notification.getId(), notification.getRecipient(), notification.getSubject());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(notification.getRecipient());
            helper.setSubject(notification.getSubject());

            // Use template or direct body
            String body = notification.usesTemplate()
                ? templateEngine.render(notification.getTemplateName(), notification.getTemplateVariables())
                : notification.getBody();

            helper.setText(body, true); // true = HTML

            // Add metadata as headers if needed
            if (notification.getMetadata() != null) {
                notification.getMetadata().forEach((key, value) -> {
                    try {
                        message.addHeader("X-" + key, value.toString());
                    } catch (MessagingException e) {
                        log.warn("Failed to add header: {}", key, e);
                    }
                });
            }

            mailSender.send(message);

            log.info("Email sent successfully: id={}", notification.getId());

        } catch (MessagingException e) {
            log.error("Failed to send email notification: id={}", notification.getId(), e);
            throw new NotificationException("Failed to send email", e);
        } catch (TemplateEngine.TemplateException e) {
            log.error("Failed to render email template: id={}, template={}",
                notification.getId(), notification.getTemplateName(), e);
            throw new NotificationException("Failed to render email template", e);
        }
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL;
    }
}
