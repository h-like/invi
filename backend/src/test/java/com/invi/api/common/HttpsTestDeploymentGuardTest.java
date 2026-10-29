package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.invi.api.media.MediaCleanupProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Component;

class HttpsTestDeploymentGuardTest {

    private static final String DATABASE = "jdbc:postgresql://db.example.test:5432/isolated_test";
    private static final String SSL = "sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory";

    @Test
    void acceptsCompleteIsolatedDeployment() {
        assertThatCode(() -> guard(validEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void isAComponentRestrictedToHttpsTestProfile() {
        assertThat(HttpsTestDeploymentGuard.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(HttpsTestDeploymentGuard.class.getAnnotation(Profile.class).value()).containsExactly("https-test");
    }

    @Test
    void usesEffectiveResolvedPropertiesNotRawSecretEnvironmentNames() {
        MockEnvironment environment = validEnvironment()
                .withProperty("APP_PUBLIC_ORIGIN", "https://invites.example.test")
                .withProperty("invi.frontend.base-url", "${APP_PUBLIC_ORIGIN}")
                .withProperty("FAKE_TEST_SIGNING_KEY", "s".repeat(64))
                .withProperty("invi.jwt.secret", "${FAKE_TEST_SIGNING_KEY}");
        assertThatCode(() -> guard(environment)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://frontend.example.test", "https://frontend.example.test/",
            "https://frontend.example.test:8443", "https://[2001:db8::2]"})
    void acceptsHttpsOrigins(String value) {
        assertThatCode(() -> guard(validEnvironment().withProperty("invi.frontend.base-url", value)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://frontend.example.test", "https://frontend.example.test/path",
            "https://frontend.example.test//", "https://frontend.example.test/%2F",
            "https://user:fake-uri-password@frontend.example.test", "https://frontend.example.test?",
            "https://frontend.example.test#", "https://localhost", "https://LOCALHOST.",
            "https://app.localhost", "https://127.0.0.1", "https://127.25.3.4", "https://0.0.0.0",
            "https://2130706433", "https://0177.0.0.1", "https://0x7f000001", "https://127.1",
            "https://[::1]", "https://[0:0:0:0:0:0:0:1]", "https://[::ffff:127.0.0.1]",
            "https://[::ffff:7f00:1]", "https://frontend.example.test:0", "https://frontend.example.test:65536",
            "https://", "not a URI fake-uri-password"})
    void rejectsUnsafeFrontendOrigins(String value) {
        rejects("invi.frontend.base-url", value);
    }

    @Test
    void acceptsUtf8SecretOfExactly64BytesRatherThan64Characters() {
        assertThatCode(() -> guard(validEnvironment().withProperty("invi.jwt.secret", "가".repeat(21) + "x")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "short-fake-signing-key", "dev-only-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "  dev-only-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void rejectsUnsafeJwtSecrets(String value) {
        rejects("invi.jwt.secret", value);
    }

    @Test
    void rejects63ByteUtf8Secret() {
        rejects("invi.jwt.secret", "가".repeat(21));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://db.internal:5432/fresh_test", "jdbc:postgresql://[2001:db8::3]/fresh_test",
            "jdbc:postgresql://database.example.org/fresh_test"})
    void acceptsHostNeutralDatabases(String value) {
        assertThatCode(() -> guard(validEnvironment().withProperty("spring.datasource.url", value + "?" + SSL)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql:isolated_test", "jdbc:mysql://db.example.test/test",
            "jdbc:postgresql:///test", "jdbc:postgresql://localhost/test", "jdbc:postgresql://127.0.0.2/test",
            "jdbc:postgresql://[::1]/test", "jdbc:postgresql://[::ffff:7f00:1]/test",
            "jdbc:postgresql://fake-user:fake-password@db.example.test/test", "jdbc:postgresql://db.example.test",
            "jdbc:postgresql://db.example.test/", "jdbc:postgresql://db.example.test/%20",
            "jdbc:postgresql://db.example.test/a/b", "jdbc:postgresql://db.example.test:65536/test"})
    void rejectsUnsafeDatabaseLocations(String value) {
        rejects("spring.datasource.url", value + "?" + SSL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sslmode=require", "sslmode=verify-full", "sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
            "sslmode=disable&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
            "sslmode=prefer&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
            "sslmode=verify-ca&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
            "sslmode=VERIFY-FULL&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
            "sslmode=verify-full&sslfactory=org.postgresql.ssl.NonValidatingFactory",
            "SSLmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
            "sslmode=verify-full&SSLFactory=org.postgresql.ssl.DefaultJavaSSLFactory"})
    void requiresExactVerifiedSslModeAndJvmTrustFactory(String query) {
        rejects("spring.datasource.url", DATABASE + "?" + query);
    }

    @ParameterizedTest
    @ValueSource(strings = {"&sslmode=require", "&sslmode=verify-full", "&SSLMode=disable", "&%73slmode=disable",
            "&sslfactory=org.postgresql.ssl.NonValidatingFactory", "&ssl=false", "&ssl=", "&user=fake-db-user",
            "&password=fake-db-password", "&%75ser=fake-db-user", "&pass%77ord=fake-db-password",
            "&USER=fake-db-user", "&username=fake-db-user", "&invalid=%GG", "&", "#fake-db-password"})
    void rejectsDuplicateConflictingOrCredentialQueryParameters(String suffix) {
        rejects("spring.datasource.url", DATABASE + "?" + SSL + suffix);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sslhostnameverifier", "SSLHOSTNAMEVERIFIER", "%73slhostnameverifier", "sslHost%6EameVerifier",
            "sslfactoryarg", "SSLFACTORYARG", "%73slfactoryarg", "sslFactory%41rg",
            "sslpassword", "SSLPASSWORD", "%73slpassword", "sslPass%77ord"})
    void rejectsCustomTlsHooksAndSslCredentialsWithoutEchoingValues(String key) {
        String secret = "fake-sensitive-tls-query-marker";
        String jdbcUrl = DATABASE + "?" + SSL + "&" + key + "=" + secret;
        assertThatThrownBy(() -> guard(validEnvironment().withProperty("spring.datasource.url", jdbcUrl)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid https-test configuration: spring.datasource.url")
                .hasMessageNotContaining(secret).hasMessageNotContaining(jdbcUrl).hasCause(null);
    }

    @Test
    void handlesPercentEncodedSslKeysAndValues() {
        String query = "%73slmode=verify%2Dfull&sslfactory=org%2Epostgresql%2Essl%2EDefaultJavaSSLFactory";
        assertThatCode(() -> guard(validEnvironment().withProperty("spring.datasource.url", DATABASE + "?" + query)))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsSafeAdditionalJdbcProperties() {
        assertThatCode(() -> guard(validEnvironment().withProperty("spring.datasource.url",
                DATABASE + "?" + SSL + "&ssl=true&connectTimeout=10&ApplicationName=https-test"
                        + "&options=endpoint%3Dep-fake-test&channelBinding=require")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.username", "spring.datasource.password", "invi.r2.access-key-id",
            "invi.r2.secret-access-key"})
    void requiresNonBlankCredentials(String property) {
        rejects(property, "  \t");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0123456789abcdef", "0123456789ABCDEF0123456789ABCDEF", "g123456789abcdef0123456789abcdef",
            "0123456789abcdef0123456789abcdef "})
    void requires32LowercaseHexAccountId(String value) {
        rejects("invi.r2.account-id", value);
    }

    @Test
    void allowsPublicImageUrlPathPrefix() {
        assertThatCode(() -> guard(validEnvironment().withProperty("invi.r2.public-base-url",
                "https://images.example.test/test-prefix/images/"))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/relative", "http://images.example.test", "https://fake-user:fake-password@images.example.test",
            "https://images.example.test/path?token=fake-image-token", "https://images.example.test/path#fragment",
            "https://localhost/images"})
    void rejectsUnsafeR2PublicBaseUrl(String value) {
        rejects("invi.r2.public-base-url", value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invi-media", "ab", "UPPERCASE-test", "invalid_bucket", "-invalid", "invalid-",
            "invalid..bucket", "invalid.bucket", "127.0.0.1"})
    void requiresValidIsolatedR2Bucket(String value) {
        rejects("invi.r2.bucket", value);
    }

    @Test
    void enforcesBucketLengthBoundaries() {
        assertThatCode(() -> guard(validEnvironment().withProperty("invi.r2.bucket", "abc"))).doesNotThrowAnyException();
        assertThatCode(() -> guard(validEnvironment().withProperty("invi.r2.bucket", "a".repeat(63))))
                .doesNotThrowAnyException();
        rejects("invi.r2.bucket", "a".repeat(64));
    }

    @Test
    void bindsCleanupPropertiesAndRejectsEnabledDeletion() {
        MockEnvironment environment = validEnvironment().withProperty("invi.r2.cleanup.enabled", "true");
        MediaCleanupProperties cleanup = Binder.get(environment)
                .bind("invi.r2.cleanup", Bindable.of(MediaCleanupProperties.class)).get();
        assertThatThrownBy(() -> new HttpsTestDeploymentGuard(environment, cleanup))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invi.r2.cleanup.enabled");
    }

    @Test
    void requiresDryRunEvenWhenCleanupIsDisabled() {
        assertThatThrownBy(() -> new HttpsTestDeploymentGuard(validEnvironment(), createMediaCleanupProperties(false, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invi.r2.cleanup.dry-run");
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "none", ""})
    void requiresFrameworkForwardHeaders(String value) {
        rejects("server.forward-headers-strategy", value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "health,info", "info", "health,env", ""})
    void exposesOnlyHealthActuator(String value) {
        rejects("management.endpoints.web.exposure.include", value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"always", "when-authorized", ""})
    void neverExposesHealthDetails(String value) {
        rejects("management.endpoint.health.show-details", value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create-drop", "create", "none", "validate", ""})
    void requiresUpdateForFreshIsolatedTestDatabase(String value) {
        rejects("spring.jpa.hibernate.ddl-auto", value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "FALSE", "", " false ", "0"})
    void requiresExactlyDisabledPublicRegistration(String value) {
        rejects("invi.auth.registration-enabled", value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "FALSE", "", " false ", "0"})
    void requiresExactlyDisabledSocialLogin(String value) {
        rejects("invi.auth.social-login-enabled", value);
    }

    @Test
    void acceptsResolvedDisabledRegistrationWithoutRequiringOwnerBootstrapFields() {
        MockEnvironment environment = validEnvironment()
                .withProperty("FAKE_TEST_REGISTRATION_ENABLED", "false")
                .withProperty("invi.auth.registration-enabled", "${FAKE_TEST_REGISTRATION_ENABLED}");
        assertThatCode(() -> guard(environment)).doesNotThrowAnyException();
    }

    @Test
    void rejectsLocalProfileEvenWhenAllEffectiveValuesAreSafe() {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("https-test", "local");
        assertThatThrownBy(() -> guard(environment)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.profiles.active");
    }

    @Test
    void localProfileConflictFailsBeanCreationAfterBindingCleanup() {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("https-test", "local");
        MediaCleanupProperties cleanup = Binder.get(environment)
                .bind("invi.r2.cleanup", Bindable.of(MediaCleanupProperties.class)).get();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.registerBean(MediaCleanupProperties.class, () -> cleanup);
            context.register(HttpsTestDeploymentGuard.class);
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("Invalid https-test configuration: spring.profiles.active");
        }
    }

    @Test
    void everyRequiredPropertyFailsClosedWhenMissing() {
        for (String name : List.of("invi.frontend.base-url", "invi.jwt.secret", "spring.datasource.url",
                "spring.datasource.username", "spring.datasource.password", "invi.r2.account-id",
                "invi.r2.access-key-id", "invi.r2.secret-access-key", "invi.r2.bucket", "invi.r2.public-base-url",
                "server.forward-headers-strategy", "management.endpoints.web.exposure.include",
                "management.endpoint.health.show-details", "spring.jpa.hibernate.ddl-auto",
                "invi.auth.registration-enabled", "invi.auth.social-login-enabled")) {
            MockEnvironment environment = validEnvironment();
            ((MapPropertySource) environment.getPropertySources().get("mockProperties")).getSource().remove(name);
            assertThatThrownBy(() -> guard(environment)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(name).hasCause(null);
        }
    }

    @Test
    void sanitizesEnvironmentResolutionErrorsWithoutKeepingTheirCause() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("invi.frontend.base-url"))
                .thenThrow(new IllegalArgumentException("fake-sensitive-resolution-value"));
        assertThatThrownBy(() -> guard(environment)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid https-test configuration: invi.frontend.base-url")
                .hasMessageNotContaining("fake-sensitive-resolution-value").hasCause(null);
    }

    @Test
    void errorsNeverEchoMalformedUrisCredentialsOrJwtValues() {
        for (String name : List.of("invi.frontend.base-url", "spring.datasource.url", "invi.jwt.secret",
                "invi.r2.public-base-url", "invi.r2.account-id", "invi.r2.bucket",
                "invi.auth.registration-enabled", "invi.auth.social-login-enabled")) {
            String sensitive = "fake-sensitive-marker";
            assertThatThrownBy(() -> guard(validEnvironment().withProperty(name, "https://user:" + sensitive + "@ bad/")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(name)
                    .hasMessageNotContaining(sensitive).hasCause(null);
        }
    }

    private static void rejects(String property, String value) {
        assertThatThrownBy(() -> guard(validEnvironment().withProperty(property, value)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid https-test configuration: " + property).hasCause(null);
    }

    private static void guard(Environment environment) {
        new HttpsTestDeploymentGuard(environment, createMediaCleanupProperties(false, true));
    }

    private static MediaCleanupProperties createMediaCleanupProperties(boolean enabled, boolean dryRun) {
        return new MediaCleanupProperties(enabled, dryRun, Duration.ofDays(7), 10000, 100);
    }

    private static MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("invi.frontend.base-url", "https://frontend.example.test")
                .withProperty("invi.jwt.secret", "fake-test-signing-key-" + "x".repeat(64))
                .withProperty("spring.datasource.url", DATABASE + "?" + SSL)
                .withProperty("spring.datasource.username", "fake-test-db-user")
                .withProperty("spring.datasource.password", "fake-test-db-password")
                .withProperty("invi.r2.account-id", "0123456789abcdef0123456789abcdef")
                .withProperty("invi.r2.access-key-id", "fake-test-access-key")
                .withProperty("invi.r2.secret-access-key", "fake-test-secret-key")
                .withProperty("invi.r2.public-base-url", "https://images.example.test")
                .withProperty("invi.r2.bucket", "invi-https-test")
                .withProperty("invi.r2.cleanup.enabled", "false")
                .withProperty("invi.r2.cleanup.dry-run", "true")
                .withProperty("server.forward-headers-strategy", "framework")
                .withProperty("management.endpoints.web.exposure.include", "health")
                .withProperty("management.endpoint.health.show-details", "never")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update")
                .withProperty("invi.auth.registration-enabled", "false")
                .withProperty("invi.auth.social-login-enabled", "false");
        environment.setActiveProfiles("https-test");
        return environment;
    }
}
