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
    private static final String ACCOUNT_ACTION_SCHEMA = "flyway_account_action_test";
    private static final String NORMALIZED_EMAIL_COLLISION_SCHEMA = "flyway_normalized_email_collision_test";
    private static final String OUTBOX_STATE_SCHEMA = "flyway_outbox_state_test";
    private static final String REFRESH_TOKEN_FAMILY_SCHEMA = "flyway_refresh_token_family_test";

    @Autowired
    FlywayMigrationIntegrationTest(DataSource dataSource) {
        super(dataSource);
    }

    @Test
    void shouldApplyEveryMigrationToAnEmptyDatabase() {
        Flyway flyway = migrationFlyway(EMPTY_DATABASE_SCHEMA, null);

        flyway.clean();
        List<String> migrationVersions = availableMigrationVersions(flyway);
        assertThat(migrationVersions).contains("9");
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

    @Test
    void versionNineBackfillsAStableFamilyForLegacyRefreshTokens() throws Exception {
        Flyway legacyFlyway = migrationFlyway(REFRESH_TOKEN_FAMILY_SCHEMA, "8");
        legacyFlyway.clean();
        legacyFlyway.migrate();

        try (Connection connection = schemaConnection(REFRESH_TOKEN_FAMILY_SCHEMA);
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (username, email, password, full_name, active, role)
                    VALUES ('v9-refresh-family', 'v9-refresh-family@example.com', 'encoded', 'V9 Refresh Family', true, 'USER')
                    """);
            statement.executeUpdate("""
                    INSERT INTO refresh_tokens (user_id, token_hash, issued_at, expires_at)
                    SELECT id, repeat('f', 64), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '7 days'
                    FROM users
                    WHERE username = 'v9-refresh-family'
                    """);

            Flyway migrationFlyway = migrationFlyway(REFRESH_TOKEN_FAMILY_SCHEMA, null);
            assertThat(migrationFlyway.migrate().success).isTrue();

            java.sql.ResultSet refreshToken = statement.executeQuery("""
                    SELECT family_id
                    FROM refresh_tokens
                    WHERE token_hash = repeat('f', 64)
                    """);
            refreshToken.next();
            assertThat(refreshToken.getObject("family_id")).isInstanceOf(java.util.UUID.class);

            java.sql.ResultSet familyColumn = statement.executeQuery("""
                    SELECT is_nullable, data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'flyway_refresh_token_family_test'
                      AND table_name = 'refresh_tokens'
                      AND column_name = 'family_id'
                    """);
            familyColumn.next();
            assertThat(familyColumn.getString("is_nullable")).isEqualTo("NO");
            assertThat(familyColumn.getString("data_type")).isEqualTo("uuid");
        }
    }

    @Test
    void versionNineBackfillsLegacyAuthenticationFieldsAndProtectsIdentityInvariants() throws Exception {
        Flyway legacyFlyway = migrationFlyway(ACCOUNT_ACTION_SCHEMA, "8");
        legacyFlyway.clean();
        legacyFlyway.migrate();

        try (Connection connection = schemaConnection(ACCOUNT_ACTION_SCHEMA);
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (username, email, password, full_name, active, role)
                    VALUES ('v9-user', ' Legacy.User@Example.COM ', 'encoded', 'V9 User', true, 'USER')
                    """);

            Flyway migrationFlyway = migrationFlyway(ACCOUNT_ACTION_SCHEMA, null);
            MigrateResult migrationResult = migrationFlyway.migrate();

            assertThat(migrationResult.success).isTrue();
            assertThat(appliedMigrationVersions(migrationFlyway)).contains("9");

            java.sql.ResultSet user = statement.executeQuery("""
                    SELECT email_normalized, verified_at, auth_version
                    FROM users
                    WHERE username = 'v9-user'
                    """);
            user.next();
            assertThat(user.getString("email_normalized")).isEqualTo("legacy.user@example.com");
            assertThat(user.getObject("verified_at")).isNotNull();
            assertThat(user.getInt("auth_version")).isZero();

            java.sql.ResultSet userUniqueConstraints = statement.executeQuery("""
                    SELECT constraint_name
                    FROM information_schema.table_constraints
                    WHERE table_schema = 'flyway_account_action_test'
                      AND table_name = 'users'
                      AND constraint_type = 'UNIQUE'
                    """);
            java.util.List<String> uniqueConstraintNames = new java.util.ArrayList<>();
            while (userUniqueConstraints.next()) {
                uniqueConstraintNames.add(userUniqueConstraints.getString("constraint_name"));
            }
            assertThat(uniqueConstraintNames)
                    .contains("uq_users_email_normalized")
                    .doesNotContain("users_email_key");

            statement.executeUpdate("""
                    INSERT INTO users (username, email, email_normalized, password, full_name, active, role, verified_at)
                    VALUES ('v9-distinct-email', ' Another.User@Example.COM ', 'another.user@example.com', 'encoded', 'Distinct Email', true, 'USER', CURRENT_TIMESTAMP)
                    """);

            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    INSERT INTO users (username, email, email_normalized, password, full_name, active, role, verified_at)
                    VALUES ('v9-duplicate', 'LEGACY.USER@example.com', 'legacy.user@example.com', 'encoded', 'Duplicate', true, 'USER', CURRENT_TIMESTAMP)
                    """))).isNotNull();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    UPDATE users
                    SET email = ' needs-normalization@example.com '
                    WHERE username = 'v9-user'
                    """))).isNotNull();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    UPDATE users
                    SET username = 'v9-user-renamed'
                    WHERE username = 'v9-user'
                    """))).isNotNull();

            statement.executeUpdate("""
                    INSERT INTO account_action_requests (
                        id, user_id, purpose, state, token_hash, token_version, issued_at, expires_at
                    )
                    SELECT
                        '00000000-0000-0000-0000-000000000091'::uuid,
                        id,
                        'VERIFY_EMAIL',
                        'PENDING',
                        repeat('a', 64),
                        1,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + INTERVAL '30 minutes'
                    FROM users
                    WHERE username = 'v9-user'
                    """);
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    INSERT INTO account_action_requests (
                        id, user_id, purpose, state, token_hash, token_version, issued_at, expires_at
                    )
                    SELECT
                        '00000000-0000-0000-0000-000000000093'::uuid,
                        id,
                        'VERIFY_EMAIL',
                        'PENDING',
                        repeat('b', 64),
                        1,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + INTERVAL '30 minutes'
                    FROM users
                    WHERE username = 'v9-user'
                    """))).isNotNull();
            statement.executeUpdate("""
                    INSERT INTO users (username, email, email_normalized, password, full_name, active, role, verified_at)
                    VALUES ('v9-other-user', 'v9-other@example.com', 'v9-other@example.com', 'encoded', 'V9 Other', true, 'USER', CURRENT_TIMESTAMP)
                    """);
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    INSERT INTO email_outbox (
                        id, recipient_user_id, account_action_request_id, kind, purpose, state, attempts, available_at
                    )
                    SELECT
                        '00000000-0000-0000-0000-000000000092'::uuid,
                        other_user.id,
                        '00000000-0000-0000-0000-000000000091'::uuid,
                        'ACCOUNT_ACTION',
                        'VERIFY_EMAIL',
                        'PENDING',
                        0,
                        CURRENT_TIMESTAMP
                    FROM users other_user
                    WHERE other_user.username = 'v9-other-user'
                    """))).isNotNull();
        }
    }

    @Test
    void versionNineRejectsNormalizedEmailCollisionsWithoutApplyingPartialSchemaChanges() throws Exception {
        Flyway legacyFlyway = migrationFlyway(NORMALIZED_EMAIL_COLLISION_SCHEMA, "8");
        legacyFlyway.clean();
        legacyFlyway.migrate();

        try (Connection connection = schemaConnection(NORMALIZED_EMAIL_COLLISION_SCHEMA);
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (username, email, password, full_name, active, role)
                    VALUES ('v9-collision-one', 'collision@example.com', 'encoded', 'Collision One', true, 'USER'),
                           ('v9-collision-two', ' COLLISION@example.com ', 'encoded', 'Collision Two', true, 'USER')
                    """);

            Flyway migrationFlyway = migrationFlyway(NORMALIZED_EMAIL_COLLISION_SCHEMA, null);

            assertThat(org.assertj.core.api.Assertions.catchThrowable(migrationFlyway::migrate)).isNotNull();
            assertThat(appliedMigrationVersions(migrationFlyway)).doesNotContain("9");

            java.sql.ResultSet emailNormalizedColumn = statement.executeQuery("""
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'flyway_normalized_email_collision_test'
                      AND table_name = 'users'
                      AND column_name = 'email_normalized'
                    """);
            emailNormalizedColumn.next();
            assertThat(emailNormalizedColumn.getInt(1)).isZero();
        }
    }

    @Test
    void versionNineEnforcesOutboxStateTimestampsAndErrorMetadata() throws Exception {
        Flyway flyway = migrationFlyway(OUTBOX_STATE_SCHEMA, null);
        flyway.clean();
        flyway.migrate();

        try (Connection connection = schemaConnection(OUTBOX_STATE_SCHEMA);
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (username, email, email_normalized, password, full_name, active, role, verified_at)
                    VALUES ('v9-outbox-user', 'v9-outbox@example.com', 'v9-outbox@example.com', 'encoded', 'V9 Outbox', true, 'USER', CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO account_action_requests (
                        id, user_id, purpose, state, token_hash, token_version, issued_at, expires_at, consumed_at
                    )
                    SELECT action.id::uuid, user_account.id, 'VERIFY_EMAIL', 'CONSUMED', action.token_hash,
                           1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 minutes', CURRENT_TIMESTAMP
                    FROM users user_account
                    CROSS JOIN (
                        VALUES
                            ('00000000-0000-0000-0000-000000000101', repeat('a', 64)),
                            ('00000000-0000-0000-0000-000000000102', repeat('b', 64)),
                            ('00000000-0000-0000-0000-000000000103', repeat('c', 64)),
                            ('00000000-0000-0000-0000-000000000104', repeat('d', 64)),
                            ('00000000-0000-0000-0000-000000000105', repeat('e', 64)),
                            ('00000000-0000-0000-0000-000000000106', repeat('f', 64))
                    ) action(id, token_hash)
                    WHERE user_account.username = 'v9-outbox-user'
                    """);

            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    INSERT INTO email_outbox (
                        id, recipient_user_id, account_action_request_id, kind, purpose, state, attempts,
                        available_at, claimed_at
                    )
                    SELECT '00000000-0000-0000-0000-000000000201'::uuid, id,
                           '00000000-0000-0000-0000-000000000101'::uuid, 'ACCOUNT_ACTION', 'VERIFY_EMAIL',
                           'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM users WHERE username = 'v9-outbox-user'
                    """))).isNotNull();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    INSERT INTO email_outbox (
                        id, recipient_user_id, account_action_request_id, kind, purpose, state, attempts, available_at
                    )
                    SELECT '00000000-0000-0000-0000-000000000202'::uuid, id,
                           '00000000-0000-0000-0000-000000000102'::uuid, 'ACCOUNT_ACTION', 'VERIFY_EMAIL',
                           'PROCESSING', 0, CURRENT_TIMESTAMP
                    FROM users WHERE username = 'v9-outbox-user'
                    """))).isNotNull();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    INSERT INTO email_outbox (
                        id, recipient_user_id, account_action_request_id, kind, purpose, state, attempts,
                        available_at, claimed_at
                    )
                    SELECT '00000000-0000-0000-0000-000000000203'::uuid, id,
                           '00000000-0000-0000-0000-000000000103'::uuid, 'ACCOUNT_ACTION', 'VERIFY_EMAIL',
                           'SENT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM users WHERE username = 'v9-outbox-user'
                    """))).isNotNull();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> statement.executeUpdate("""
                    INSERT INTO email_outbox (
                        id, recipient_user_id, account_action_request_id, kind, purpose, state, attempts, available_at
                    )
                    SELECT '00000000-0000-0000-0000-000000000204'::uuid, id,
                           '00000000-0000-0000-0000-000000000104'::uuid, 'ACCOUNT_ACTION', 'VERIFY_EMAIL',
                           'DEAD', 0, CURRENT_TIMESTAMP
                    FROM users WHERE username = 'v9-outbox-user'
                    """))).isNotNull();

            assertThat(statement.executeUpdate("""
                    INSERT INTO email_outbox (
                        id, recipient_user_id, account_action_request_id, kind, purpose, state, attempts,
                        available_at, claimed_at, sent_at
                    )
                    SELECT '00000000-0000-0000-0000-000000000205'::uuid, id,
                           '00000000-0000-0000-0000-000000000105'::uuid, 'ACCOUNT_ACTION', 'VERIFY_EMAIL',
                           'SENT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM users WHERE username = 'v9-outbox-user'
                    """)).isEqualTo(1);
            assertThat(statement.executeUpdate("""
                    INSERT INTO email_outbox (
                        id, recipient_user_id, account_action_request_id, kind, purpose, state, attempts,
                        available_at, last_error_code
                    )
                    SELECT '00000000-0000-0000-0000-000000000206'::uuid, id,
                           '00000000-0000-0000-0000-000000000106'::uuid, 'ACCOUNT_ACTION', 'VERIFY_EMAIL',
                           'DEAD', 1, CURRENT_TIMESTAMP, 'DELIVERY_RETRY_EXHAUSTED'
                    FROM users WHERE username = 'v9-outbox-user'
                    """)).isEqualTo(1);
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

    private Connection schemaConnection(String schema) throws java.sql.SQLException {
        Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
        connection.setSchema(schema);
        return connection;
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
