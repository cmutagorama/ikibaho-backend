package com.charlie.ikibaho.identity;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises V13 against data, not an empty schema.
 *
 * The rest of the suite starts from a fresh database, so every migration runs
 * over nothing -- which means the backfill, the part of V13 that can actually
 * destroy something, was never executed by any test. This one migrates to V12,
 * writes rows in the old shape, and only then applies V13.
 *
 * Deliberately no Spring context: ddl-auto=validate would reject the V12 schema,
 * because the entities describe the world after V13.
 */
class GlobalAccountMigrationTest {

    @Test
    void backfillsMembershipsBeforeDroppingTheColumnItReads() {
        try (PostgreSQLContainer<?> postgres =
                     new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))) {
            postgres.start();
            DataSource dataSource = dataSourceFor(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            migrateTo(dataSource, "12");
            seedLegacyRows(jdbc);

            migrateTo(dataSource, null);   // apply V13

            // Both users kept their workspace, and the admin kept being an admin.
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member", Long.class))
                    .isEqualTo(2L);
            assertThat(jdbc.queryForObject("""
                    SELECT m.role FROM organization_member m
                    JOIN app_user u ON u.id = m.user_id
                    WHERE u.email = 'boss@acme.test'
                    """, String.class)).isEqualTo("ADMIN");
            assertThat(jdbc.queryForObject("""
                    SELECT m.role FROM organization_member m
                    JOIN app_user u ON u.id = m.user_id
                    WHERE u.email = 'worker@acme.test'
                    """, String.class)).isEqualTo("MEMBER");

            // The invited user's membership inherits their pending state rather
            // than silently becoming active.
            assertThat(jdbc.queryForObject("""
                    SELECT m.status FROM organization_member m
                    JOIN app_user u ON u.id = m.user_id
                    WHERE u.email = 'worker@acme.test'
                    """, String.class)).isEqualTo("INVITED");

            // The account shape actually changed.
            assertThat(columnExists(jdbc, "app_user", "organization_id")).isFalse();
            assertThat(columnExists(jdbc, "app_user", "global_role")).isFalse();
            assertThat(columnExists(jdbc, "refresh_token", "organization_id")).isTrue();

            // And the existing session still knows which workspace it belongs to,
            // so nobody is logged out by the deploy.
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM refresh_token WHERE organization_id IS NOT NULL", Long.class))
                    .isEqualTo(1L);
        }
    }

    /**
     * Two accounts in one org: an active admin and an invited member with no
     * password, which is the row V7 made possible and V13 has to carry over.
     */
    private void seedLegacyRows(JdbcTemplate jdbc) {
        UUID org = UUID.randomUUID();
        UUID boss = UUID.randomUUID();
        UUID worker = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO organization (id, name, slug, created_at, updated_at)
                VALUES (?, 'Acme', 'acme', now(), now())
                """, org);

        jdbc.update("""
                INSERT INTO app_user (id, organization_id, email, password_hash, display_name,
                                      status, global_role, email_verified, created_at, updated_at)
                VALUES (?, ?, 'boss@acme.test', 'hash', 'Boss', 'ACTIVE', 'SITE_ADMIN', true, now(), now())
                """, boss, org);

        jdbc.update("""
                INSERT INTO app_user (id, organization_id, email, password_hash, display_name,
                                      status, global_role, email_verified, created_at, updated_at)
                VALUES (?, ?, 'worker@acme.test', NULL, 'Worker', 'INVITED', 'USER', false, now(), now())
                """, worker, org);

        jdbc.update("""
                INSERT INTO refresh_token (id, user_id, family_id, token_hash, expires_at,
                                           created_at, updated_at)
                VALUES (?, ?, ?, 'tokenhash', now() + interval '30 days', now(), now())
                """, UUID.randomUUID(), boss, UUID.randomUUID());
    }

    private static void migrateTo(DataSource dataSource, String version) {
        var configuration = Flyway.configure().dataSource(dataSource);
        if (version != null) {
            configuration.target(MigrationVersion.fromVersion(version));
        }
        configuration.load().migrate();
    }

    private static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """, Long.class, table, column);
        return count != null && count > 0;
    }

    private static DataSource dataSourceFor(PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }
}
