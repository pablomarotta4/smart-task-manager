package com.pablomarotta.smart_task_manager.exception;

import com.pablomarotta.smart_task_manager.dto.ErrorDetails;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void responseStatusExceptionsKeepTheirIntendedHttpStatus() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/projects/20/members/18");

        ResponseEntity<ErrorDetails> response = new GlobalExceptionHandler()
                .handleResponseStatusException(
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Project owner cannot be removed"
                        ),
                        request
                );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Project owner cannot be removed", response.getBody().getMessage());
    }

    @Test
    void dataIntegrityConflictsDoNotExposeDatabaseDetails() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/tasks/101/status");

        ErrorDetails response = new GlobalExceptionHandler().handleDataIntegrityViolationException(
                new DataIntegrityViolationException("fk_tasks_project_assignee_membership detail"),
                request
        );

        assertEquals(409, response.getStatus());
        assertEquals("Request conflicts with current resource state", response.getMessage());
    }

    @Test
    void unexpectedErrorsUseTheGenericContractWithoutDisclosingTheCause() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/auth/password-reset/confirm");

        ErrorDetails response = new GlobalExceptionHandler().handleGlobalException(
                new IllegalStateException("raw token and database endpoint must not reach the client"),
                request
        );

        assertEquals(500, response.getStatus());
        assertEquals("Unexpected server error", response.getMessage());
        assertEquals("Request failed", response.getDetails());
    }
}
