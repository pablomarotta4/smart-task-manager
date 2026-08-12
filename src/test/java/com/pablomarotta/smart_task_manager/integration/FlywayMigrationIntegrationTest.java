package com.pablomarotta.smart_task_manager.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationIntegrationTest extends PostgresIntegrationTest {

    private static final String EMPTY_DATABASE_SCHEMA = "flyway_empty_database_test";
    private static final String SEQUENTIAL_UPGRADE_SCHEMA = "flyway_sequential_upgrade_test";

    @Autowired
    FlywayMigrationIntegrationTest(DataSource dataSource) {
        super(dataSource);
    }

    @Test
    void shouldApplyEveryMigrationToAnEmptyDatabase() {
        Flyway flyway = migrationFlyway(EMPTY_DATABASE_SCHEMA, null);

        flyway.clean();
        List<String> migrationVersions = availableMigrationVersions(flyway);
        MigrateResult migrationResult = flyway.migrate();

        assertThat(migrationResult.success).isTrue();
        assertThat(appliedMigrationVersions(flyway)).containsExactlyElementsOf(migrationVersions);
    }

    @Test
    void shouldUpgradeAnExistingDatabaseOneMigrationAtATime() {
        Flyway initialFlyway = migrationFlyway(SEQUENTIAL_UPGRADE_SCHEMA, null);
        initialFlyway.clean();
        List<String> migrationVersions = availableMigrationVersions(initialFlyway);

        for (int index = 0; index < migrationVersions.size(); index++) {
            String migrationVersion = migrationVersions.get(index);
            Flyway upgradeFlyway = migrationFlyway(SEQUENTIAL_UPGRADE_SCHEMA, migrationVersion);
            MigrateResult migrationResult = upgradeFlyway.migrate();

            assertThat(migrationResult.success).isTrue();
            assertThat(appliedMigrationVersions(upgradeFlyway))
                    .containsExactlyElementsOf(migrationVersions.subList(0, index + 1));
        }
    }

    private Flyway migrationFlyway(String schema, String targetMigrationVersion) {
        org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(false)
                .createSchemas(true);
        if (targetMigrationVersion != null) {
            configuration.target(targetMigrationVersion);
        }
        return configuration.load();
    }

    private List<String> availableMigrationVersions(Flyway flyway) {
        return Arrays.stream(flyway.info().pending())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();
    }

    private List<String> appliedMigrationVersions(Flyway flyway) {
        return Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
    }
}
