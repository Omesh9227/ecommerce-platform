package com.ecommerce.notificationservice.mail;

import com.ecommerce.notificationservice.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Renders an email (HTML from templates/email/notification.html, plus a plain-text version for clients that
 * don't show HTML) and sends it over SMTP. Locally that SMTP server is Mailpit.
 *
 * @throws org.springframework.mail.MailException if the mail server can't be reached (the Kafka event is retried)
 */
@Component
@RequiredArgsConstructor
public class EmailSender {

    static final String TEMPLATE = "email/notification";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties properties;

    public void send(String to, EmailContent content) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(to);
            helper.setSubject(content.subject());
            helper.setText(plainText(content), html(content));
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("Could not build email '" + content.subject() + "'", ex);
        }
        mailSender.send(message);
    }

    String html(EmailContent content) {
        Context context = new Context();
        context.setVariable("content", content);
        context.setVariable("storeName", properties.storeName());
        context.setVariable("supportEmail", properties.supportEmail());
        return templateEngine.process(TEMPLATE, context);
    }

    String plainText(EmailContent content) {
        StringBuilder text = new StringBuilder();
        text.append(content.heading()).append("\n\n").append(content.intro()).append("\n\n");
        content.details().forEach(d -> text.append(d.label()).append(": ").append(d.value()).append('\n'));
        if (!content.items().isEmpty()) {
            text.append('\n');
            content.items().forEach(i -> text.append("  ").append(i.quantity()).append(" x ").append(i.name())
                    .append("  ").append(i.lineTotal()).append('\n'));
        }
        if (content.total() != null) {
            text.append("Total: ").append(content.total()).append('\n');
        }
        if (content.outro() != null) {
            text.append('\n').append(content.outro()).append('\n');
        }
        text.append("\n-- \n").append(properties.storeName()).append(" - questions? ").append(properties.supportEmail()).append('\n');
        return text.toString();
    }
}
