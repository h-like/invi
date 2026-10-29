package com.invi.api.common;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.LocalAuthController.LoginRequest;
import com.invi.api.account.LocalAuthController.RegisterRequest;
import com.invi.api.account.LocalAuthService;
import com.invi.api.account.MemberRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Creates only the configured test owner at startup, never through an HTTP endpoint. */
@Component
@Profile("https-test")
public class HttpsTestOwnerBootstrap implements ApplicationRunner {

    private final LocalAuthService localAuthService;
    private final MemberRepository memberRepository;
    // Intentionally no generated toString, logging, or credential-bearing exception causes.
    private final String ownerEmail;
    private final String ownerPassword;

    public HttpsTestOwnerBootstrap(
            LocalAuthService localAuthService, MemberRepository memberRepository, Environment environment) {
        this.localAuthService = localAuthService;
        this.memberRepository = memberRepository;
        try {
            String email = environment.getProperty("invi.https-test.owner.email");
            String password = environment.getProperty("invi.https-test.owner.password");
            if (email == null || password == null || password.isBlank()
                    || password.length() < 8 || password.length() > 64
                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw initializationFailed();
            }
            // Reuse the backend request's @NotBlank/@Email/@Size contract without
            // creating a credential DTO or retaining constraint violations in an error.
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                if (!factory.getValidator().validateValue(RegisterRequest.class, "email", email.strip().toLowerCase(Locale.ROOT)).isEmpty()) {
                    throw initializationFailed();
                }
            }
            this.ownerEmail = email;
            this.ownerPassword = password;
        } catch (RuntimeException ignored) {
            throw initializationFailed();
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String email = ownerEmail.strip().toLowerCase(Locale.ROOT);
            if (memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email).isEmpty()) {
                try {
                    localAuthService.register(new RegisterRequest(email, ownerPassword, "HTTPS 테스트"));
                } catch (ConflictException ignored) {
                    // Another startup may have created the same owner. Login below
                    // must still verify the configured credentials, including in this race.
                }
            }
            // Verify both existing and newly created owners. Never reset a password
            // or expose/retain the token issued by either service method.
            localAuthService.login(new LoginRequest(email, ownerPassword));
        } catch (RuntimeException ignored) {
            throw initializationFailed();
        }
    }

    private static IllegalStateException initializationFailed() {
        return new IllegalStateException("HTTPS test owner initialization failed.");
    }
}
