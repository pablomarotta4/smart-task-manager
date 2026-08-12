package com.pablomarotta.smart_task_manager.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailDeliveryTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsTheInMemoryMessageThroughSpringMail() {
        SmtpEmailDelivery delivery = new SmtpEmailDelivery(mailSender, "no-reply@example.test");

        delivery.deliver(new EmailDelivery.Message("recipient@example.test", "Subject", "Body"));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@example.test");
        assertThat(message.getTo()).containsExactly("recipient@example.test");
        assertThat(message.getSubject()).isEqualTo("Subject");
        assertThat(message.getText()).isEqualTo("Body");
    }

    @Test
    void convertsMailFailuresToAStableDeliveryCategory() {
        SmtpEmailDelivery delivery = new SmtpEmailDelivery(mailSender, "no-reply@example.test");
        doThrow(new org.springframework.mail.MailSendException("unavailable"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatThrownBy(() -> delivery.deliver(new EmailDelivery.Message("recipient@example.test", "Subject", "Body")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("SMTP_DELIVERY_FAILED");
    }
}
