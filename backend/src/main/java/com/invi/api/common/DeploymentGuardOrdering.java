package com.invi.api.common;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs the deployment guards before Flyway touches any database. Flyway applies its customizers
 * while it is being built, so asking for the guards here makes a misconfigured deployment (say,
 * production pointed at the test database) stop before a single migration runs against it.
 */
@Configuration(proxyBeanMethods = false)
class DeploymentGuardOrdering {

    @Bean
    FlywayConfigurationCustomizer deploymentGuardsBeforeMigrations(
            ObjectProvider<HttpsTestDeploymentGuard> httpsTest, ObjectProvider<ProdDeploymentGuard> prod) {
        httpsTest.getIfAvailable();
        prod.getIfAvailable();
        return configuration -> {};
    }
}
