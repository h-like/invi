package com.invi.api.common;

import com.invi.api.invitation.InvitationExpiryProperties;
import com.invi.api.media.MediaCleanupProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Validates effective deployment settings without connecting to any external service. */
@Component
@Profile("https-test")
public class HttpsTestDeploymentGuard {

    public HttpsTestDeploymentGuard(Environment environment, MediaCleanupProperties cleanup,
            InvitationExpiryProperties expiry) {
        DeploymentChecks checks = new DeploymentChecks("https-test", environment);
        checks.require(!environment.acceptsProfiles(Profiles.of("local")), "spring.profiles.active");
        checks.requireHardenedBaseline();

        checks.require(cleanup != null && !cleanup.enabled(), "invi.r2.cleanup.enabled");
        checks.require(cleanup.dryRun(), "invi.r2.cleanup.dry-run");
        // Test data never expires for real; switching off dry-run is a production decision.
        checks.require(expiry != null && expiry.dryRun(), "invi.invitation.expiry.dry-run");
        checks.exact("invi.auth.registration-enabled", "false");
        checks.exact("invi.auth.social-login-enabled", "false");
    }
}
