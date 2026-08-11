package com.pablomarotta.smart_task_manager.exception;

import com.pablomarotta.smart_task_manager.dto.ErrorDetails;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void illegalArgumentsAreReportedAsBadRequests() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/tasks/101/assign");

        ErrorDetails response = new GlobalExceptionHandler().handleIllegalArgumentException(
                new IllegalArgumentException("User is not a member of this project"),
                request
        );

        assertEquals(400, response.getStatus());
        assertEquals("User is not a member of this project", response.getMessage());
    }
}
