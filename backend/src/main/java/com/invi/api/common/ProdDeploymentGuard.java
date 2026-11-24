package com.invi.api.common;

import com.invi.api.media.MediaCleanupProperties;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start production on settings that belong to another environment or that are not
 * safe yet. Keeps every https-test hardening rule; signup, social login and the expiry batch are
 * production decisions and stay configurable.
 */
@Component
@Profile("prod")
public class ProdDeploymentGuard {

    public ProdDeploymentGuard(Environment environment, MediaCleanupProperties cleanup) {
        DeploymentChecks checks = new DeploymentChecks("prod", environment);
        checks.require(!environment.acceptsProfiles(Profiles.of("local", "https-test")), "spring.profiles.active");
        checks.requireHardenedBaseline();

        // The test database and bucket hold fake data and their own photo references. A plain
        // substring match on purpose: a false positive only means naming the new resource
        // differently, while a missed test resource would mix test data into production.
        checks.require(!checks.databaseName().toLowerCase(Locale.ROOT).contains("test"), DeploymentChecks.DATASOURCE_URL);
        checks.require(!checks.property("invi.r2.bucket").toLowerCase(Locale.ROOT).contains("test"), "invi.r2.bucket");

        // Physical R2 deletion is unverified against conditional DELETE (docs/MEDIA_CLEANUP.md).
        checks.require(cleanup != null && !cleanup.enabled(), "invi.r2.cleanup.enabled");

        String socialLogin = checks.property("invi.auth.social-login-enabled");
        checks.require(socialLogin.equals("true") || socialLogin.equals("false"), "invi.auth.social-login-enabled");
        if (socialLogin.equals("true")) {
            for (String provider : new String[] {"kakao", "google"}) {
                String prefix = "spring.security.oauth2.client.registration." + provider;
                checks.nonBlank(prefix + ".client-id");
                checks.nonBlank(prefix + ".client-secret");
            }
        }
        String registration = checks.property("invi.auth.registration-enabled");
        checks.require(registration.equals("true") || registration.equals("false"), "invi.auth.registration-enabled");

        String limit = checks.property("invi.invitation.max-per-member");
        checks.require(limit.matches("[1-9][0-9]{0,2}"), "invi.invitation.max-per-member");
    }
}
