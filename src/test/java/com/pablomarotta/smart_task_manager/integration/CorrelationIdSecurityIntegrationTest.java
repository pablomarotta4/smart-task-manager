package com.pablomarotta.smart_task_manager.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdSecurityIntegrationTest extends PostgresIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdSecurityIntegrationTest.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String SAFE_CORRELATION_ID = "security-chain_2026.08:12";

    private final MockMvc mockMvc;

    @Autowired
    CorrelationIdSecurityIntegrationTest(DataSource dataSource, MockMvc mockMvc) {
        super(dataSource);
        this.mockMvc = mockMvc;
    }

    @Test
    void safeCorrelationIdIsReturnedOnUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/tasks/alltasks")
                        .header(CORRELATION_ID_HEADER, SAFE_CORRELATION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CORRELATION_ID_HEADER, SAFE_CORRELATION_ID));
    }

    @Test
    void unsafeCorrelationIdIsReplacedOnUnauthorizedResponse() throws Exception {
        String unsafeCorrelationId = "unsafe=correlation-id";

        String outboundCorrelationId = mockMvc.perform(get("/api/tasks/alltasks")
                        .header(CORRELATION_ID_HEADER, unsafeCorrelationId))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getHeader(CORRELATION_ID_HEADER);

        UUID generatedCorrelationId = UUID.fromString(outboundCorrelationId);
        assertEquals(4, generatedCorrelationId.version());
    }

    @Test
    void consolePatternMakesCorrelationIdObservable(CapturedOutput output) {
        MDC.put(CORRELATION_ID_MDC_KEY, SAFE_CORRELATION_ID);
        try {
            LOGGER.info("correlation configuration probe");
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }

        assertTrue(output.getOut().contains("correlationId=" + SAFE_CORRELATION_ID));
    }
}
