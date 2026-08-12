package com.pablomarotta.smart_task_manager.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailDelivery implements EmailDelivery {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailDelivery(
            JavaMailSender mailSender,
            @Value("${email-outbox.from-address}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void deliver(Message message) {
        SimpleMailMessage smtpMessage = new SimpleMailMessage();
        smtpMessage.setFrom(fromAddress);
        smtpMessage.setTo(message.recipientEmail());
        smtpMessage.setSubject(message.subject());
        smtpMessage.setText(message.body());
        try {
            mailSender.send(smtpMessage);
        } catch (MailException exception) {
            throw new EmailDeliveryException("SMTP_DELIVERY_FAILED", exception);
        }
    }
}
