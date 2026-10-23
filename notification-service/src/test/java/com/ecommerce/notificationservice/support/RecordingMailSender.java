package com.ecommerce.notificationservice.support;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A mail sender for tests: keeps sent messages in memory instead of using SMTP, and can pretend the server is down. */
public class RecordingMailSender extends JavaMailSenderImpl {

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        if (failing) {
            throw new MailSendException("Mail server unreachable (test)");
        }
        sent.addAll(Arrays.asList(mimeMessages));
    }

    public List<MimeMessage> sent() {
        return sent;
    }

    public void clear() {
        sent.clear();
        failing = false;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    /** Every text part (HTML and plain text) of a message, concatenated. */
    public static String allText(Part part) {
        try {
            Object content = part.getContent();
            if (content instanceof String text) {
                return text;
            }
            if (content instanceof Multipart multipart) {
                StringBuilder all = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart body = multipart.getBodyPart(i);
                    all.append(allText(body)).append('\n');
                }
                return all.toString();
            }
            return "";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
