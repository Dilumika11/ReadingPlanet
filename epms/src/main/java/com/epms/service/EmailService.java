package com.epms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends e-mails when a mail server is configured (SPRING_MAIL_HOST etc.);
 * otherwise writes the message to the log so links can still be used
 * during development. Sending never fails the calling action.
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, @Value("${epms.mail.from:no-reply@readingplanet.lk}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Returns true if the e-mail was handed to the mail server. */
    public boolean send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("E-mail not configured; would send to {}: {}\n{}", to, subject, body);
            return false;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            sender.send(m);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not send e-mail to {} ({}): {}", to, subject, e.getMessage());
            return false;
        }
    }
}
