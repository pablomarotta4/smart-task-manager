package com.pablomarotta.smart_task_manager.config;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIdPolicy {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final int MAXIMUM_CORRELATION_ID_LENGTH = 128;
    private static final int MAXIMUM_TRAILING_CORRELATION_ID_CHARACTERS =
            MAXIMUM_CORRELATION_ID_LENGTH - 1;
    private static final Pattern SAFE_CORRELATION_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,"
                    + MAXIMUM_TRAILING_CORRELATION_ID_CHARACTERS + "}");

    private CorrelationIdPolicy() {
    }

    public static String resolve(String candidate) {
        if (isSafe(candidate)) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    public static boolean isSafe(String candidate) {
        return candidate != null && SAFE_CORRELATION_ID_PATTERN.matcher(candidate).matches();
    }
}
