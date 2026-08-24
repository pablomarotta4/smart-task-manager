package com.pablomarotta.smart_task_manager.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresIntegrationTestGuardTest {

    private static final String DEVELOPER_POSTGRES_JDBC_URL = "jdbc:postgresql://localhost:5433/smart_task_manager";

    @Test
    void shouldRejectDestructiveCleanupAgainstNonContainerDatasource() {
        assertThatThrownBy(() -> PostgresIntegrationTest.requireTestcontainerJdbcUrl(DEVELOPER_POSTGRES_JDBC_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Testcontainers PostgreSQL");
    }
}
