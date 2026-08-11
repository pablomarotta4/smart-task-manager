package com.pablomarotta.smart_task_manager.client;

import com.pablomarotta.smart_task_manager.config.AIPlanningProperties;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningRequest;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class FastApiAIPlanningClient implements AIPlanningClient {
    private final RestClient restClient;

    @Autowired
    public FastApiAIPlanningClient(AIPlanningProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    FastApiAIPlanningClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AIPlanningResponse generatePlan(
            UUID runId,
            String prompt,
            AIPlanningContext context
    ) {
        try {
            AIPlanningResponse response = restClient.post()
                    .uri("/internal/v1/project-plans")
                    .body(new AIPlanningRequest(runId, prompt, context))
                    .retrieve()
                    .body(AIPlanningResponse.class);
            if (response == null) {
                throw new AIPlanningUnavailableException("AI planning service returned an empty response");
            }
            if (!runId.equals(response.runId())
                    || !"v1".equals(response.contractVersion())
                    || response.draft() == null
                    || response.quality() == null) {
                throw new AIPlanningUnavailableException("AI planning service returned a mismatched contract");
            }
            return response;
        } catch (RestClientException exception) {
            throw new AIPlanningUnavailableException("AI planning service is unavailable", exception);
        }
    }
}
