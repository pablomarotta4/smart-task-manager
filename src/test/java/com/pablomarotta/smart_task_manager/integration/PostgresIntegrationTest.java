package com.pablomarotta.smart_task_manager.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Tag("integration")
@Testcontainers
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTest {

    private static final String DATABASE_NAME = "smart_task_manager_integration";
    private static final String DATABASE_USERNAME = "integration";
    private static final String DATABASE_PASSWORD = "integration";

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName(DATABASE_NAME)
            .withUsername(DATABASE_USERNAME)
            .withPassword(DATABASE_PASSWORD);

    private final DataSource dataSource;

    protected PostgresIntegrationTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @DynamicPropertySource
    static void configureContainerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @BeforeEach
    final void verifyContainerDatasourceBeforeDestructiveTestSetup() {
        requireTestcontainerJdbcUrl(resolveJdbcUrl());
    }

    static void requireTestcontainerJdbcUrl(String jdbcUrl) {
        if (!POSTGRESQL.isRunning() || !POSTGRESQL.getJdbcUrl().equals(jdbcUrl)) {
            throw new IllegalStateException(
                    "Destructive integration tests require the Testcontainers PostgreSQL datasource"
            );
        }
    }

    private String resolveJdbcUrl() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return connection.getMetaData().getURL();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to verify the integration-test datasource", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
