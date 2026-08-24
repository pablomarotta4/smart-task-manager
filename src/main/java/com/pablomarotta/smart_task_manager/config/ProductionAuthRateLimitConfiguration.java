package com.pablomarotta.smart_task_manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionAuthRateLimitConfiguration {

    private static final String EDGE_ENFORCED = "edge-enforced";
    private static final String SINGLE_INSTANCE = "single-instance";

    public ProductionAuthRateLimitConfiguration(
            @Value("${auth-rate-limit.enforcement-mode}") String enforcementMode,
            @Value("${auth-rate-limit.single-instance-acknowledged:false}") boolean singleInstanceAcknowledged,
            @Value("${auth-rate-limit.trusted-proxies}") String trustedProxies
    ) {
        String normalizedMode = enforcementMode == null ? "" : enforcementMode.strip().toLowerCase(Locale.ROOT);
        if (EDGE_ENFORCED.equals(normalizedMode)) {
            if (trustedProxies == null || trustedProxies.isBlank()) {
                throw new IllegalStateException(
                        "Production edge-enforced auth rate limiting requires explicit trusted proxies"
                );
            }
            return;
        }
        if (SINGLE_INSTANCE.equals(normalizedMode) && singleInstanceAcknowledged) {
            return;
        }
        throw new IllegalStateException(
                "Production auth rate limiting requires edge-enforced or acknowledged single-instance mode"
        );
    }
}
