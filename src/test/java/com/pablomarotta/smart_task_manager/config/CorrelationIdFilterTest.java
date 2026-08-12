package com.pablomarotta.smart_task_manager.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelationIdFilterTest {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_MDC_KEY = "correlationId";
    private static final String SAFE_CORRELATION_ID = "spring-request_2026.08:12";
    private static final String UNSAFE_CORRELATION_ID = "invalid correlation id";
    private static final String UNSAFE_PUNCTUATED_CORRELATION_ID = "invalid=correlation-id";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final String MAX_LENGTH_CORRELATION_ID = "a".repeat(MAX_CORRELATION_ID_LENGTH);
    private static final String OVERLONG_CORRELATION_ID = "a".repeat(MAX_CORRELATION_ID_LENGTH + 1);
    private static final int UUID_VERSION_FOUR = 4;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesSafeCorrelationIdToResponseAndDownstreamMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> downstreamCorrelationId = new AtomicReference<>();
        request.addHeader(CORRELATION_HEADER, SAFE_CORRELATION_ID);

        new CorrelationIdFilter().doFilter(
                request,
                response,
                (downstreamRequest, downstreamResponse) ->
                        downstreamCorrelationId.set(MDC.get(CORRELATION_MDC_KEY))
        );

        assertEquals(SAFE_CORRELATION_ID, response.getHeader(CORRELATION_HEADER));
        assertEquals(SAFE_CORRELATION_ID, downstreamCorrelationId.get());
        assertNull(MDC.get(CORRELATION_MDC_KEY));
    }

    @Test
    void replacesUnsafeCorrelationIdWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CORRELATION_HEADER, UNSAFE_CORRELATION_ID);

        new CorrelationIdFilter().doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
        });

        assertGeneratedCorrelationId(response);
    }

    @Test
    void replacesCorrelationIdContainingUnsafeCharactersWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CORRELATION_HEADER, UNSAFE_PUNCTUATED_CORRELATION_ID);

        new CorrelationIdFilter().doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
        });

        assertGeneratedCorrelationId(response);
    }

    @Test
    void replacesOverlongCorrelationIdWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CORRELATION_HEADER, OVERLONG_CORRELATION_ID);

        new CorrelationIdFilter().doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
        });

        assertGeneratedCorrelationId(response);
    }

    @Test
    void acceptsCorrelationIdAtMaximumLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CORRELATION_HEADER, MAX_LENGTH_CORRELATION_ID);

        new CorrelationIdFilter().doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
        });

        assertEquals(MAX_LENGTH_CORRELATION_ID, response.getHeader(CORRELATION_HEADER));
    }

    @Test
    void generatesCorrelationIdWhenRequestDoesNotProvideOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
        });

        assertGeneratedCorrelationId(response);
    }

    @Test
    void restoresExistingMdcCorrelationIdAfterRequest() throws Exception {
        String outerCorrelationId = "outer-request-2026-08-12";
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(CORRELATION_MDC_KEY, outerCorrelationId);

        new CorrelationIdFilter().doFilter(request, response, (downstreamRequest, downstreamResponse) -> {
        });

        assertEquals(outerCorrelationId, MDC.get(CORRELATION_MDC_KEY));
    }

    @Test
    void clearsMdcWhenDownstreamFilterThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CORRELATION_HEADER, SAFE_CORRELATION_ID);

        assertThrows(
                IllegalStateException.class,
                () -> new CorrelationIdFilter().doFilter(
                        request,
                        response,
                        (downstreamRequest, downstreamResponse) -> {
                            throw new IllegalStateException();
                        }
                )
        );

        assertNull(MDC.get(CORRELATION_MDC_KEY));
    }

    private void assertGeneratedCorrelationId(MockHttpServletResponse response) {
        String generatedCorrelationId = response.getHeader(CORRELATION_HEADER);
        UUID generatedUuid = assertDoesNotThrow(() -> UUID.fromString(generatedCorrelationId));
        assertEquals(UUID_VERSION_FOUR, generatedUuid.version());
    }
}
