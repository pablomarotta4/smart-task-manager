package com.pablomarotta.smart_task_manager.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
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
        assertThat(migrationVersions).contains("8");
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

    @Test
    void versionEightRepairsRolesAndProtectsOwnerMembershipInvariants() throws Exception {
        String schema = "flyway_project_role_invariant_test";
        Flyway legacyFlyway = migrationFlyway(schema, "7");
        legacyFlyway.clean();
        legacyFlyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        )) {
            connection.setSchema(schema);
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users (username, email, password, full_name, active, role)
                        VALUES ('v8-owner', 'v8-owner@example.com', 'encoded', 'V8 Owner', true, 'USER'),
                               ('v8-member', 'v8-member@example.com', 'encoded', 'V8 Member', true, 'USER'),
                               ('v8-default', 'v8-default@example.com', 'encoded', 'V8 Default', true, 'USER')
                        """);
                statement.executeUpdate("""
                        INSERT INTO projects (name, owner_id)
                        SELECT 'V8 project', id FROM users WHERE username = 'v8-owner'
                        """);
                statement.executeUpdate("""
                        INSERT INTO project_memberships (project_id, user_id)
                        SELECT project.id, user_account.id
                        FROM projects project
                        CROSS JOIN users user_account
                        WHERE project.name = 'V8 project' AND user_account.username = 'v8-member'
                        """);
                statement.executeUpdate("""
                        INSERT INTO tasks (project_id, title, status, assignee_id)
                        SELECT project.id, 'Legacy invalid assignment', 'TODO', user_account.id
                        FROM projects project
                        CROSS JOIN users user_account
                        WHERE project.name = 'V8 project' AND user_account.username = 'v8-default'
                        """);
                statement.executeUpdate("""
                        INSERT INTO tasks (project_id, title, status, assignee_id)
                        SELECT project.id, 'Revocation race assignment', 'TODO', user_account.id
                        FROM projects project
                        CROSS JOIN users user_account
                        WHERE project.name = 'V8 project' AND user_account.username = 'v8-member'
                        """);
            }

            Flyway upgradeFlyway = migrationFlyway(schema, null);
            upgradeFlyway.migrate();

            try (java.sql.Statement statement = connection.createStatement()) {
                java.sql.ResultSet roles = statement.executeQuery("""
                        SELECT membership.role
                        FROM project_memberships membership
                        JOIN users user_account ON user_account.id = membership.user_id
                        WHERE user_account.username = 'v8-member'
                        """);
                roles.next();
                assertThat(roles.getString("role")).isEqualTo("MEMBER");

                java.sql.ResultSet repairedOwner = statement.executeQuery("""
                        SELECT membership.role
                        FROM project_memberships membership
                        JOIN users user_account ON user_account.id = membership.user_id
                        WHERE user_account.username = 'v8-owner'
                        """);
                repairedOwner.next();
                assertThat(repairedOwner.getString("role")).isEqualTo("OWNER");

                java.sql.ResultSet repairedLegacyAssignment = statement.executeQuery("""
                        SELECT assignee_id
                        FROM tasks
                        WHERE title = 'Legacy invalid assignment'
                        """);
                repairedLegacyAssignment.next();
                assertThat(repairedLegacyAssignment.getObject("assignee_id")).isNull();

                statement.executeUpdate("""
                        INSERT INTO project_memberships (project_id, user_id)
                        SELECT project.id, user_account.id
                        FROM projects project
                        CROSS JOIN users user_account
                        WHERE project.name = 'V8 project' AND user_account.username = 'v8-default'
                        """);
                java.sql.ResultSet defaultRole = statement.executeQuery("""
                        SELECT membership.role
                        FROM project_memberships membership
                        JOIN users user_account ON user_account.id = membership.user_id
                        WHERE user_account.username = 'v8-default'
                        """);
                defaultRole.next();
                assertThat(defaultRole.getString("role")).isEqualTo("MEMBER");

                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                        UPDATE project_memberships
                        SET role = 'INVALID'
                        WHERE user_id = (SELECT id FROM users WHERE username = 'v8-default')
                        """))).isNotNull();
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                        UPDATE project_memberships
                        SET role = 'OWNER'
                        WHERE user_id = (SELECT id FROM users WHERE username = 'v8-member')
                        """))).isNotNull();
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                        UPDATE project_memberships
                        SET user_id = (SELECT id FROM users WHERE username = 'v8-default')
                        WHERE user_id = (SELECT id FROM users WHERE username = 'v8-member')
                        """))).isNotNull();
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                        DELETE FROM project_memberships
                        WHERE user_id = (SELECT id FROM users WHERE username = 'v8-owner')
                        """))).isNotNull();
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                        UPDATE project_memberships
                        SET role = 'MEMBER'
                        WHERE user_id = (SELECT id FROM users WHERE username = 'v8-owner')
                        """))).isNotNull();
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                        UPDATE projects
                        SET owner_id = (SELECT id FROM users WHERE username = 'v8-default')
                        WHERE name = 'V8 project'
                        """))).isNotNull();

                statement.executeUpdate("""
                        DELETE FROM project_memberships
                        WHERE user_id = (SELECT id FROM users WHERE username = 'v8-member')
                        """);
                java.sql.ResultSet clearedRevokedAssignment = statement.executeQuery("""
                        SELECT assignee_id
                        FROM tasks
                        WHERE title = 'Revocation race assignment'
                        """);
                clearedRevokedAssignment.next();
                assertThat(clearedRevokedAssignment.getObject("assignee_id")).isNull();

                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                        UPDATE tasks
                        SET assignee_id = (SELECT id FROM users WHERE username = 'v8-member')
                        WHERE title = 'Revocation race assignment'
                        """))).isNotNull();
                java.sql.ResultSet rejectedStaleAssignment = statement.executeQuery("""
                        SELECT assignee_id
                        FROM tasks
                        WHERE title = 'Revocation race assignment'
                        """);
                rejectedStaleAssignment.next();
                assertThat(rejectedStaleAssignment.getObject("assignee_id")).isNull();

                statement.executeUpdate("DELETE FROM projects WHERE name = 'V8 project'");
                java.sql.ResultSet projectCount = statement.executeQuery(
                        "SELECT count(*) FROM projects WHERE name = 'V8 project'"
                );
                projectCount.next();
                assertThat(projectCount.getLong(1)).isZero();
            }
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
