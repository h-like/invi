package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Inspect the public production YAML without resolving any real secret or connecting to a DB. */
class ProdProfileContractTest {
    private static Properties profile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    @Test
    void secretSettingsHaveNoFallbacks() {
        Properties properties = profile();
        assertThat(properties.getProperty("spring.datasource.url")).isEqualTo("${DATABASE_JDBC_URL}");
        assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${DATABASE_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${DATABASE_PASSWORD}");
        assertThat(properties.getProperty("invi.jwt.secret")).isEqualTo("${JWT_SECRET}");
        assertThat(properties.getProperty("invi.r2.bucket")).isEqualTo("${R2_BUCKET}");
        assertThat(properties.getProperty("spring.security.oauth2.client.registration.kakao.client-secret"))
                .isEqualTo("${KAKAO_CLIENT_SECRET}");
        assertThat(properties.getProperty("spring.security.oauth2.client.registration.google.client-secret"))
                .isEqualTo("${GOOGLE_CLIENT_SECRET}");
    }

    @Test
    void opensSignupButKeepsDeletionSafe() {
        Properties properties = profile();
        assertThat(properties.getProperty("invi.auth.registration-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("invi.auth.social-login-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("invi.r2.cleanup.enabled")).isEqualTo("false");
        // Report-only unless the deployment explicitly says otherwise.
        assertThat(properties.getProperty("invi.invitation.expiry.dry-run")).isEqualTo("${INVITATION_EXPIRY_DRY_RUN:true}");
        assertThat(properties.getProperty("invi.invitation.max-per-member")).isEqualTo("${INVITATION_MAX_PER_MEMBER:3}");
    }

    @Test
    void keepsTheDeploymentHardening() {
        Properties properties = profile();
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(properties.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(properties.getProperty("management.endpoint.health.group.readiness.include")).isEqualTo("readinessState,db");
        assertThat(properties.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("0");
        assertThat(properties.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("OFF");
        assertThat(properties.getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }
}
