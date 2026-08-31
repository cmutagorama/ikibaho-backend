package com.charlie.ikibaho.notification.internal.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Outbound email.
 * <p>
 * Off by default. Delivery is a phase 9 concern (digests, templates, retry), and
 * a half-wired sender that throws on every notification would take the whole
 * listener down with it -- turning "we could not send an email" into "the
 * history line was never written", since both hang off the same event.
 * <p>
 * So when it is disabled this logs and returns, and when it is enabled a send
 * failure is caught rather than propagated. Losing an email must not cost the
 * in-app notification that is the actual phase 6 deliverable.
 */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;

    EmailSender(ObjectProvider<JavaMailSender> mailSender,
                @Value("${ikibaho.notifications.email.enabled:false}") boolean enabled,
                @Value("${ikibaho.notifications.email.from:no-reply@ikibaho.local}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("Email suppressed (disabled): to={} subject={}", to, subject);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Email enabled but no JavaMailSender configured; dropping: to={}", to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (MailException e) {
            log.error("Failed to send notification email to {}: {}", to, e.getMessage());
        }
    }
}
