package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Inspect public YAML without resolving any real environment secret or connecting to a DB. */
class HttpsTestProfileContractTest {
    private static Properties profile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-https-test.yml"));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    @Test
    void secretSettingsHaveNoDevelopmentFallbacks() {
        Properties properties = profile();
        assertThat(properties.getProperty("spring.datasource.url")).isEqualTo("${TEST_DATABASE_JDBC_URL}");
        assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${TEST_DATABASE_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${TEST_DATABASE_PASSWORD}");
        assertThat(properties.getProperty("invi.jwt.secret")).isEqualTo("${JWT_SECRET}");
        assertThat(properties.getProperty("invi.r2.bucket")).isEqualTo("${TEST_R2_BUCKET}");
        assertThat(properties.getProperty("invi.https-test.owner.email")).isEqualTo("${TEST_OWNER_EMAIL}");
        assertThat(properties.getProperty("invi.https-test.owner.password")).isEqualTo("${TEST_OWNER_PASSWORD}");
        assertThat(properties.getProperty("invi.frontend.base-url")).isEqualTo("${APP_PUBLIC_ORIGIN:${RENDER_EXTERNAL_URL:}}");
    }

    @Test
    void testProfileClosesSignupOauthAndPhysicalCleanup() {
        Properties properties = profile();
        assertThat(properties.getProperty("invi.auth.registration-enabled")).isEqualTo("false");
        assertThat(properties.getProperty("invi.auth.social-login-enabled")).isEqualTo("false");
        assertThat(properties.getProperty("invi.r2.cleanup.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("invi.r2.cleanup.dry-run")).isEqualTo("true");
        assertThat(properties.getProperty("invi.invitation.expiry.dry-run")).isEqualTo("true");
    }

    @Test
    void readinessAndLogLevelsRemainSafeWhenYamlIsActuallyParsed() {
        Properties properties = profile();
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(properties.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(properties.getProperty("management.endpoint.health.group.readiness.include")).isEqualTo("readinessState,db");
        assertThat(properties.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        // Unquoted OFF is a YAML 1.1 boolean and would become an invalid logging level.
        assertThat(properties.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("OFF");
        assertThat(properties.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("OFF");
        assertThat(properties.getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }
}
