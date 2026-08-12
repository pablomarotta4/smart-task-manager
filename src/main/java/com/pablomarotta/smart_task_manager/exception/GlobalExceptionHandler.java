package com.pablomarotta.smart_task_manager.exception;

import com.pablomarotta.smart_task_manager.dto.ErrorDetails;
import com.pablomarotta.smart_task_manager.dto.AccountActionErrorResponse;
import com.pablomarotta.smart_task_manager.security.AuthRateLimitExceededException;
import com.pablomarotta.smart_task_manager.client.AIPlanningUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected server error";
    private static final String UNEXPECTED_ERROR_DETAILS = "Request failed";

    @ExceptionHandler(AccountActionException.class)
    public org.springframework.http.ResponseEntity<AccountActionErrorResponse> handleAccountActionException(
            AccountActionException exception
    ) {
        return org.springframework.http.ResponseEntity.status(exception.getCode().status())
                .body(new AccountActionErrorResponse(exception.getCode().name()));
    }

    @ExceptionHandler(AuthRateLimitExceededException.class)
    public org.springframework.http.ResponseEntity<ErrorDetails> handleAuthRateLimitExceededException(
            AuthRateLimitExceededException exception,
            WebRequest request
    ) {
        ErrorDetails response = new ErrorDetails(
                LocalDateTime.now(),
                exception.getMessage(),
                request.getDescription(false),
                HttpStatus.TOO_MANY_REQUESTS.value()
        );
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDetails handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return new ErrorDetails(
                LocalDateTime.now(),
                message,
                request.getDescription(false),
                HttpStatus.BAD_REQUEST.value()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDetails handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ErrorDetails(
                LocalDateTime.now(),
                message,
                request.getDescription(false),
                HttpStatus.BAD_REQUEST.value()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDetails handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.BAD_REQUEST.value()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDetails handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            WebRequest request
    ) {
        return new ErrorDetails(
                LocalDateTime.now(),
                "Request conflicts with current resource state",
                request.getDescription(false),
                HttpStatus.CONFLICT.value()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDetails handleGlobalException(Exception ex, WebRequest request) {
        LOG.error("Unhandled server exception type={}", ex.getClass().getName());
        return new ErrorDetails(
                LocalDateTime.now(),
                UNEXPECTED_ERROR_MESSAGE,
                UNEXPECTED_ERROR_DETAILS,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDetails handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.UNAUTHORIZED.value()
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDetails handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.UNAUTHORIZED.value()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorDetails handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.FORBIDDEN.value()
        );
    }

    @ExceptionHandler(UserDuplicatedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDetails handleUserDuplicatedException(UserDuplicatedException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.CONFLICT.value()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDetails handleUserNotFoundException(UserNotFoundException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.NOT_FOUND.value()
        );
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDetails handleProjectNotFoundException(ProjectNotFoundException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.NOT_FOUND.value()
        );
    }

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDetails handleTaskNotFoundException(TaskNotFoundException ex, WebRequest request) {
        return new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.NOT_FOUND.value()
        );
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ErrorDetails> handleResponseStatusException(org.springframework.web.server.ResponseStatusException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getReason(),
                request.getDescription(false),
                ex.getStatusCode().value()
        );
        return new org.springframework.http.ResponseEntity<>(errorDetails, ex.getStatusCode());
    }

    @ExceptionHandler(AIPlanningUnavailableException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorDetails handleAIPlanningUnavailableException(
            AIPlanningUnavailableException ex,
            WebRequest request
    ) {
        return new ErrorDetails(
                LocalDateTime.now(),
                "AI planning service is unavailable",
                request.getDescription(false),
                HttpStatus.BAD_GATEWAY.value()
        );
    }
}
