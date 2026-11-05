package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The path the https-test database takes on its first Flyway boot: a schema that ddl-auto
 * built at 51e6e1b (no template.code, no RSVP side/meal, 255-char guestbook messages,
 * KAKAO/GOOGLE-only provider check) is baselined at V1 and V2 brings it up to V1.
 * CI's empty database only exercises V1 then V2, so this covers the other branch.
 * Runs in a throwaway schema of the real PostgreSQL, dropped afterwards; the app's own
 * migration of the default schema is off so only this test's Flyway runs.
 */
@JdbcTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlywayLegacyBaselineTest {

    private static final String MEMBER = "00000000-0000-0000-0000-000000000001";
    private static final String TEMPLATE = "00000000-0000-0000-0000-000000000002";
    private static final String INVITATION = "00000000-0000-0000-0000-000000000003";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private String schema;

    @BeforeEach
    void buildLegacySchema() throws Exception {
        schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        String v1 = new ClassPathResource("db/migration/V1__baseline.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);
            sql.execute(v1);
            sql.execute("""
                    ALTER TABLE template DROP COLUMN code;
                    ALTER TABLE rsvp_response DROP COLUMN side, DROP COLUMN meal;
                    ALTER TABLE guestbook_entry ALTER COLUMN message TYPE varchar(255);
                    ALTER TABLE member DROP CONSTRAINT member_provider_check;
                    ALTER TABLE member ADD CONSTRAINT member_provider_check CHECK (provider IN ('KAKAO', 'GOOGLE'));
                    INSERT INTO member VALUES ('%1$s', now(), now(), 'a@example.invalid', 'a', null, 'KAKAO', 'k1');
                    INSERT INTO template VALUES ('%2$s', now(), now(), 'seal', null, 'Seal', null);
                    INSERT INTO invitation VALUES ('%3$s', now(), now(), null, null, '{"blocks":[]}', 'FREE',
                        'legacy-slug', 'PUBLISHED', '2026-12-01', '%1$s', '%2$s');
                    INSERT INTO rsvp_response VALUES (gen_random_uuid(), now(), now(), true, 2, 'old guest', 'hi', '%3$s');
                    """.formatted(MEMBER, TEMPLATE, INVITATION));
        }
    }

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void baselinesAtV1AndCatchesUpWithV2KeepingData() {
        migrate(true);

        assertThat(jdbc.queryForList(
                "SELECT version || ':' || type FROM " + schema + ".flyway_schema_history ORDER BY installed_rank",
                String.class)).containsExactly("1:BASELINE", "2:SQL", "3:SQL");
        // V3: existing invitations start at version 0.
        assertThat(jdbc.queryForObject("SELECT version FROM " + schema + ".invitation", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT guest_name || '/' || coalesce(side, '-') || '/' || coalesce(meal, '-') FROM " + schema + ".rsvp_response",
                String.class)).isEqualTo("old guest/-/-");
        assertThat(jdbc.queryForObject("SELECT slug FROM " + schema + ".invitation", String.class)).isEqualTo("legacy-slug");
        assertThat(jdbc.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = 'guestbook_entry' AND column_name = 'message'",
                Integer.class, schema)).isEqualTo(500);

        // Email/password accounts now pass the provider check, and RSVP values are checked.
        jdbc.update("INSERT INTO " + schema + ".member (id, created_at, updated_at, email, name, provider, provider_id)"
                + " VALUES (gen_random_uuid(), now(), now(), 'b@example.invalid', 'b', 'LOCAL', 'l1')");
        jdbc.update("INSERT INTO " + schema + ".rsvp_response (id, created_at, updated_at, attending, guest_count, guest_name, meal, side, invitation_id)"
                + " VALUES (gen_random_uuid(), now(), now(), true, 1, 'new', 'YES', 'BRIDE', ?::uuid)", INVITATION);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".rsvp_response (id, created_at, updated_at, attending, guest_count, guest_name, side, invitation_id)"
                + " VALUES (gen_random_uuid(), now(), now(), true, 1, 'bad', 'UNCLE', ?::uuid)", INVITATION))
                .isInstanceOf(DataIntegrityViolationException.class);

        // template.code is back and unique.
        jdbc.update("UPDATE " + schema + ".template SET code = 'seal'");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".template (id, created_at, updated_at, category, code, name) VALUES (gen_random_uuid(), now(), now(), 'seal', 'seal', 'Copy')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A second boot finds nothing to do.
        assertThat(migrate(true).migrationsExecuted).isZero();
    }

    @Test
    void refusesNonEmptySchemaWithoutHistoryUnlessBaselineIsOn() {
        assertThatThrownBy(() -> migrate(false)).hasMessageContaining("non-empty schema");
        assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name = 'flyway_schema_history'",
                String.class, schema)).isEqualTo(List.of());
    }

    private org.flywaydb.core.api.output.MigrateResult migrate(boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .createSchemas(false)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1")
                .load()
                .migrate();
    }
}
