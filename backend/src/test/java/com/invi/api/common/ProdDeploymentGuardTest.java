package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invi.api.media.MediaCleanupProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Component;

/** The shared hardening rules are covered in HttpsTestDeploymentGuardTest; this covers prod's own. */
class ProdDeploymentGuardTest {

    private static final String SSL = "sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory";

    @Test
    void acceptsCompleteProductionDeployment() {
        assertThatCode(() -> guard(validEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void isAComponentRestrictedToProdProfile() {
        assertThat(ProdDeploymentGuard.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(ProdDeploymentGuard.class.getAnnotation(Profile.class).value()).containsExactly("prod");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "https-test"})
    void refusesToRunAlongsideAnotherProfile(String other) {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("prod", other);
        assertThatThrownBy(() -> guard(environment)).hasMessage("Invalid prod configuration: spring.profiles.active");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invi_https_test", "TEST", "invi_test_copy"})
    void refusesATestDatabase(String name) {
        rejects("spring.datasource.url", "jdbc:postgresql://db.example.org/" + name + "?" + SSL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invi-media-https-test", "test-bucket", "invi-media"})
    void refusesTheTestOrLocalBucket(String bucket) {
        rejects("invi.r2.bucket", bucket);
    }

    @Test
    void keepsTheSharedHardeningRules() {
        rejects("invi.jwt.secret", "dev-only-" + "x".repeat(64));
        rejects("spring.datasource.url", "jdbc:postgresql://db.example.org/invi?sslmode=require");
        rejects("invi.frontend.base-url", "http://invi.example.org");
        rejects("management.endpoints.web.exposure.include", "health,info");
        rejects("spring.jpa.hibernate.ddl-auto", "update");
    }

    @Test
    void refusesPhysicalR2Cleanup() {
        assertThatThrownBy(() -> new ProdDeploymentGuard(validEnvironment(), cleanup(true)))
                .hasMessage("Invalid prod configuration: invi.r2.cleanup.enabled");
    }

    @Test
    void socialLoginNeedsBothProvidersCredentials() {
        for (String name : List.of("kakao.client-id", "kakao.client-secret", "google.client-id", "google.client-secret")) {
            rejects("spring.security.oauth2.client.registration." + name, " ");
        }
    }

    @Test
    void socialLoginCanBeTurnedOffWithoutProviderCredentials() {
        MockEnvironment environment = validEnvironment().withProperty("invi.auth.social-login-enabled", "false");
        for (String name : List.of("kakao.client-id", "kakao.client-secret", "google.client-id", "google.client-secret")) {
            ((MapPropertySource) environment.getPropertySources().get("mockProperties")).getSource()
                    .remove("spring.security.oauth2.client.registration." + name);
        }
        assertThatCode(() -> guard(environment)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "yes", ""})
    void signupFlagsMustBeExactBooleans(String value) {
        rejects("invi.auth.registration-enabled", value);
        rejects("invi.auth.social-login-enabled", value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "", "1000", "three"})
    void requiresAPositiveInvitationLimit(String value) {
        rejects("invi.invitation.max-per-member", value);
    }

    private static void rejects(String property, String value) {
        assertThatThrownBy(() -> guard(validEnvironment().withProperty(property, value)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid prod configuration: " + property).hasCause(null);
    }

    private static void guard(MockEnvironment environment) {
        new ProdDeploymentGuard(environment, cleanup(false));
    }

    private static MediaCleanupProperties cleanup(boolean enabled) {
        return new MediaCleanupProperties(enabled, true, Duration.ofDays(7), 10000, 100);
    }

    private static MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("invi.frontend.base-url", "https://invi.example.org")
                .withProperty("invi.jwt.secret", "fake-prod-signing-key-" + "x".repeat(64))
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.org:5432/invi?" + SSL)
                .withProperty("spring.datasource.username", "fake-db-user")
                .withProperty("spring.datasource.password", "fake-db-password")
                .withProperty("invi.r2.account-id", "0123456789abcdef0123456789abcdef")
                .withProperty("invi.r2.access-key-id", "fake-access-key")
                .withProperty("invi.r2.secret-access-key", "fake-secret-key")
                .withProperty("invi.r2.public-base-url", "https://images.example.org")
                .withProperty("invi.r2.bucket", "invi-media-prod")
                .withProperty("server.forward-headers-strategy", "framework")
                .withProperty("management.endpoints.web.exposure.include", "health")
                .withProperty("management.endpoint.health.show-details", "never")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("invi.auth.registration-enabled", "true")
                .withProperty("invi.auth.social-login-enabled", "true")
                .withProperty("spring.security.oauth2.client.registration.kakao.client-id", "fake-kakao-id")
                .withProperty("spring.security.oauth2.client.registration.kakao.client-secret", "fake-kakao-secret")
                .withProperty("spring.security.oauth2.client.registration.google.client-id", "fake-google-id")
                .withProperty("spring.security.oauth2.client.registration.google.client-secret", "fake-google-secret")
                .withProperty("invi.invitation.max-per-member", "3");
        environment.setActiveProfiles("prod");
        return environment;
    }
}
