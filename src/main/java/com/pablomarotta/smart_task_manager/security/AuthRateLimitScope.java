package com.pablomarotta.smart_task_manager.security;

public enum AuthRateLimitScope {
    LOGIN,
    REGISTER,
    PASSWORD_RESET_REQUEST,
    PASSWORD_RESET_CONFIRM,
    EMAIL_VERIFICATION_CONFIRM,
    EMAIL_VERIFICATION_RESEND
}
