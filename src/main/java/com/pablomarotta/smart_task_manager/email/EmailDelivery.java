package com.pablomarotta.smart_task_manager.email;

public interface EmailDelivery {

    void deliver(Message message);

    record Message(String recipientEmail, String subject, String body) {
    }
}
