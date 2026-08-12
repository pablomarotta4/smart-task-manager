package com.pablomarotta.smart_task_manager.config;

import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import com.pablomarotta.smart_task_manager.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationContractTest {

    private static final String PRODUCTION_RATE_LIMIT_CONFIGURATION =
            "com.pablomarotta.smart_task_manager.config.ProductionAuthRateLimitConfiguration";

    @Test
    void baseProfileDoesNotSupplyTokenSecretsAndCannotStartWithoutThem() {
        Properties base = readProperties("application.yaml");

        assertThat(base)
                .containsEntry("jwt.secret", "${JWT_SECRET}")
                .containsEntry("account-action.token-secret", "${ACCOUNT_ACTION_TOKEN_SECRET}");

        contextWith(base, TokenCodecConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void localIntegrationAndProductionProfilesHaveExplicitSecurityContracts() {
        assertThat(new ClassPathResource("application-local.yaml").exists()).isTrue();

        Properties local = merge("application.yaml", "application-local.yaml");
        assertThat(local)
                .containsKeys("jwt.secret", "account-action.token-secret", "auth-rate-limit.trusted-proxies");
        contextWith(local, TokenCodecConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed());

        Properties integration = merge("application.yaml", "application-integration.yaml");
        assertThat(integration)
                .containsKeys("jwt.secret", "account-action.token-secret", "auth-rate-limit.trusted-proxies");
        contextWith(integration, TokenCodecConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed());

        Properties production = merge("application.yaml", "application-prod.yaml");
        assertThat(production)
                .containsEntry("auth-rate-limit.enforcement-mode", "${AUTH_RATE_LIMIT_ENFORCEMENT_MODE}")
                .containsEntry("auth-rate-limit.trusted-proxies", "${AUTH_RATE_LIMIT_TRUSTED_PROXIES}");

        Class<?> productionRateLimitConfiguration = productionRateLimitConfiguration();
        contextWith(production, TokenCodecConfiguration.class, productionRateLimitConfiguration)
                .withPropertyValues(productionProperties())
                .run(context -> assertThat(context).hasFailed());
        contextWith(production, TokenCodecConfiguration.class, productionRateLimitConfiguration)
                .withPropertyValues(productionProperties(
                        "AUTH_RATE_LIMIT_ENFORCEMENT_MODE=single-instance"))
                .run(context -> assertThat(context).hasFailed());
        contextWith(production, TokenCodecConfiguration.class, productionRateLimitConfiguration)
                .withPropertyValues(productionProperties(
                        "AUTH_RATE_LIMIT_ENFORCEMENT_MODE=edge-enforced",
                        "AUTH_RATE_LIMIT_TRUSTED_PROXIES="))
                .run(context -> assertThat(context).hasFailed());
        contextWith(production, TokenCodecConfiguration.class, productionRateLimitConfiguration)
                .withPropertyValues(productionProperties(
                        "AUTH_RATE_LIMIT_ENFORCEMENT_MODE=edge-enforced",
                        "AUTH_RATE_LIMIT_TRUSTED_PROXIES=192.0.2.10"))
                .run(context -> assertThat(context).hasNotFailed());
        contextWith(production, TokenCodecConfiguration.class, productionRateLimitConfiguration)
                .withPropertyValues(productionProperties(
                        "AUTH_RATE_LIMIT_ENFORCEMENT_MODE=single-instance",
                        "AUTH_RATE_LIMIT_SINGLE_INSTANCE_ACKNOWLEDGED=true",
                        "AUTH_RATE_LIMIT_TRUSTED_PROXIES="))
                .run(context -> assertThat(context).hasNotFailed());
    }

    private ApplicationContextRunner contextWith(Properties properties, Class<?>... configurationClasses) {
        Map<String, Object> configuration = new HashMap<>();
        properties.forEach((key, value) -> configuration.put(String.valueOf(key), value));
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("security-configuration-contract", configuration)
                ))
                .withUserConfiguration(configurationClasses);
    }

    private String[] productionTokenProperties() {
        return new String[]{
                "JWT_SECRET=security-configuration-test-access-secret-1234567890",
                "JWT_ISSUER=security-configuration-test-access",
                "JWT_AUDIENCE=security-configuration-test-api",
                "ACCOUNT_ACTION_TOKEN_SECRET=security-configuration-test-action-secret-1234567890",
                "ACCOUNT_ACTION_TOKEN_ISSUER=security-configuration-test-action",
                "ACCOUNT_ACTION_TOKEN_AUDIENCE=security-configuration-test-action-api"
        };
    }

    private String[] productionProperties(String... additionalProperties) {
        String[] tokenProperties = productionTokenProperties();
        String[] combined = java.util.Arrays.copyOf(
                tokenProperties,
                tokenProperties.length + additionalProperties.length + 1
        );
        combined[tokenProperties.length] = "spring.profiles.active=prod";
        System.arraycopy(additionalProperties, 0, combined, tokenProperties.length + 1, additionalProperties.length);
        return combined;
    }

    private Class<?> productionRateLimitConfiguration() {
        try {
            return Class.forName(PRODUCTION_RATE_LIMIT_CONFIGURATION);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Production auth rate-limit configuration is required", exception);
        }
    }

    private Properties merge(String... resourceNames) {
        Properties merged = new Properties();
        for (String resourceName : resourceNames) {
            merged.putAll(readProperties(resourceName));
        }
        return merged;
    }

    private Properties readProperties(String resourceName) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resourceName));
        return factory.getObject();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({JwtTokenProvider.class, ActionTokenCodec.class})
    static class TokenCodecConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
