package com.invi.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.LocalAuthController.LoginRequest;
import com.invi.api.account.LocalAuthController.RegisterRequest;
import com.invi.api.account.LocalAuthService;
import com.invi.api.account.Member;
import com.invi.api.account.MemberRepository;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class HttpsTestOwnerBootstrapTest {

    private static final String EMAIL_PROPERTY = "invi.https-test.owner.email";
    private static final String PASSWORD_PROPERTY = "invi.https-test.owner.password";
    private static final String EMAIL = "owner@example.test";
    private static final String PASSWORD = "fake-owner-password";
    private static final String FAILURE = "HTTPS test owner initialization failed.";

    @Mock private LocalAuthService localAuthService;
    @Mock private MemberRepository memberRepository;
    @Mock private Environment environment;

    @Test
    void isAStartupRunnerComponentRestrictedToHttpsTest() {
        assertThat(ApplicationRunner.class.isAssignableFrom(HttpsTestOwnerBootstrap.class)).isTrue();
        assertThat(HttpsTestOwnerBootstrap.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(HttpsTestOwnerBootstrap.class.getAnnotation(Profile.class).value()).containsExactly("https-test");
    }

    @Test
    void constructorOnlyValidatesAndNeverTouchesRepositoryOrService() {
        bootstrap(validEnvironment());
        verifyNoInteractions(memberRepository, localAuthService);
    }

    @Test
    void absentLocalOwnerIsNormalizedCreatedThenVerified() {
        when(memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL)).thenReturn(Optional.empty());
        var runner = bootstrap(validEnvironment().withProperty(EMAIL_PROPERTY, " \tOWNER@EXAMPLE.TEST\n"));

        runner.run(null);

        var order = inOrder(memberRepository, localAuthService);
        order.verify(memberRepository).findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL);
        var registration = ArgumentCaptor.forClass(RegisterRequest.class);
        order.verify(localAuthService).register(registration.capture());
        assertThat(registration.getValue().email()).isEqualTo(EMAIL);
        assertThat(registration.getValue().password()).isEqualTo(PASSWORD);
        assertThat(registration.getValue().name()).isEqualTo("HTTPS 테스트");
        order.verify(localAuthService).login(new LoginRequest(EMAIL, PASSWORD));
        verifyNoMoreInteractions(memberRepository, localAuthService);
    }

    @Test
    void existingLocalOwnerIsOnlyVerifiedNeverRecreatedOrUpdated() {
        existingOwner(EMAIL);

        bootstrap(validEnvironment()).run(null);

        verify(memberRepository).findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL);
        verify(localAuthService).login(new LoginRequest(EMAIL, PASSWORD));
        verifyNoMoreInteractions(memberRepository, localAuthService);
    }

    @Test
    void registrationRaceConflictStillRequiresSuccessfulLogin() {
        when(memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL)).thenReturn(Optional.empty());
        when(localAuthService.register(any())).thenThrow(new ConflictException("fake-sensitive-race-detail"));

        assertThatCode(() -> bootstrap(validEnvironment()).run(null)).doesNotThrowAnyException();

        var order = inOrder(localAuthService);
        order.verify(localAuthService).register(new RegisterRequest(EMAIL, PASSWORD, "HTTPS 테스트"));
        order.verify(localAuthService).login(new LoginRequest(EMAIL, PASSWORD));
        verifyNoMoreInteractions(localAuthService);
    }

    @Test
    void wrongExistingOwnerPasswordAbortsWithoutResetOrRegistration() {
        existingOwner(EMAIL);
        when(localAuthService.login(any())).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, EMAIL + " " + PASSWORD));

        assertSanitizedFailure(() -> bootstrap(validEnvironment()).run(null));

        verify(memberRepository).findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL);
        verify(localAuthService).login(new LoginRequest(EMAIL, PASSWORD));
        verify(localAuthService, never()).register(any());
        verifyNoMoreInteractions(memberRepository, localAuthService);
    }

    @Test
    void raceConflictCannotBypassCredentialVerification() {
        when(memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL)).thenReturn(Optional.empty());
        when(localAuthService.register(any())).thenThrow(new ConflictException(PASSWORD));
        when(localAuthService.login(any())).thenThrow(new IllegalArgumentException(PASSWORD));

        assertSanitizedFailure(() -> bootstrap(validEnvironment()).run(null));

        verify(localAuthService).login(new LoginRequest(EMAIL, PASSWORD));
    }

    @Test
    void registrationFailureIsSanitizedAndDoesNotTryLogin() {
        when(memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL)).thenReturn(Optional.empty());
        when(localAuthService.register(any())).thenThrow(new IllegalStateException(EMAIL + " " + PASSWORD));

        assertSanitizedFailure(() -> bootstrap(validEnvironment()).run(null));

        verify(localAuthService, never()).login(any());
    }

    @Test
    void repositoryFailureIsSanitizedBeforeServiceCalls() {
        when(memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, EMAIL))
                .thenThrow(new IllegalStateException(EMAIL + " " + PASSWORD));

        assertSanitizedFailure(() -> bootstrap(validEnvironment()).run(null));

        verifyNoInteractions(localAuthService);
    }

    @Test
    void resolvesEffectivePropertiesRatherThanReadingRawEnvironmentVariableNames() {
        var configured = validEnvironment()
                .withProperty("FAKE_OWNER_EMAIL", " OWNER@EXAMPLE.TEST ")
                .withProperty("FAKE_OWNER_PASSWORD", PASSWORD)
                .withProperty(EMAIL_PROPERTY, "${FAKE_OWNER_EMAIL}")
                .withProperty(PASSWORD_PROPERTY, "${FAKE_OWNER_PASSWORD}");
        existingOwner(EMAIL);

        bootstrap(configured).run(null);

        verify(localAuthService).login(new LoginRequest(EMAIL, PASSWORD));
    }

    @ParameterizedTest
    @ValueSource(strings = {EMAIL_PROPERTY, PASSWORD_PROPERTY})
    void propertyResolutionErrorsNeverRetainSecretValuesOrCauses(String property) {
        if (PASSWORD_PROPERTY.equals(property)) {
            when(environment.getProperty(EMAIL_PROPERTY)).thenReturn(EMAIL);
        }
        when(environment.getProperty(property)).thenThrow(new IllegalArgumentException(EMAIL + " " + PASSWORD));

        assertSanitizedFailure(() -> bootstrap(environment));

        verifyNoInteractions(memberRepository, localAuthService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "not-an-email", "owner@", "@example.test", "owner name@example.test",
            "owner@@example.test", "owner\n@example.test"})
    void invalidOrMissingEmailFailsBeforeAnyDatabaseOrServiceCall(String value) {
        var configured = new MockEnvironment().withProperty(PASSWORD_PROPERTY, PASSWORD);
        if (value != null) {
            configured.withProperty(EMAIL_PROPERTY, value);
        }

        assertSanitizedFailure(() -> bootstrap(configured));

        verifyNoInteractions(memberRepository, localAuthService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "        ", "\t\n      ", "1234567"})
    void invalidOrMissingPasswordFailsBeforeAnyDatabaseOrServiceCall(String value) {
        var configured = new MockEnvironment().withProperty(EMAIL_PROPERTY, EMAIL);
        if (value != null) {
            configured.withProperty(PASSWORD_PROPERTY, value);
        }

        assertSanitizedFailure(() -> bootstrap(configured));

        verifyNoInteractions(memberRepository, localAuthService);
    }

    @Test
    void rejectsPasswordsOverCharacterOrUtf8ByteLimits() {
        for (String password : new String[] {"a".repeat(65), "가".repeat(24) + "a", "가".repeat(25)}) {
            assertSanitizedFailure(() -> bootstrap(validEnvironment().withProperty(PASSWORD_PROPERTY, password)));
        }
        verifyNoInteractions(memberRepository, localAuthService);
    }

    @Test
    void acceptsEightAnd64CharactersAndExactly72Utf8BytesWithoutChangingPassword() {
        existingOwner(EMAIL);
        for (String password : new String[] {"12345678", "a".repeat(64), "가".repeat(24), "  password  "}) {
            bootstrap(validEnvironment().withProperty(PASSWORD_PROPERTY, password)).run(null);
            verify(localAuthService).login(new LoginRequest(EMAIL, password));
        }
        verify(localAuthService, never()).register(any());
    }

    @Test
    void enforcesEmail254CharacterBoundaryWithOtherwiseValidEmailSyntax() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertThat(email).hasSize(254);
        assertThatCode(() -> bootstrap(validEnvironment().withProperty(EMAIL_PROPERTY, email)))
                .doesNotThrowAnyException();
        assertSanitizedFailure(() -> bootstrap(validEnvironment().withProperty(EMAIL_PROPERTY, email + "d")));
        verifyNoInteractions(memberRepository, localAuthService);
    }

    @Test
    void normalizationUsesRootLocaleRatherThanHostDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            existingOwner("invite@example.test");
            bootstrap(validEnvironment().withProperty(EMAIL_PROPERTY, " INVITE@EXAMPLE.TEST ")).run(null);
            verify(localAuthService).login(new LoginRequest("invite@example.test", PASSWORD));
        } finally {
            Locale.setDefault(original);
        }
    }

    private void existingOwner(String email) {
        when(memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email))
                .thenReturn(Optional.of(Member.builder().build()));
    }

    private HttpsTestOwnerBootstrap bootstrap(Environment configured) {
        return new HttpsTestOwnerBootstrap(localAuthService, memberRepository, configured);
    }

    private static MockEnvironment validEnvironment() {
        return new MockEnvironment().withProperty(EMAIL_PROPERTY, EMAIL).withProperty(PASSWORD_PROPERTY, PASSWORD);
    }

    private static void assertSanitizedFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(IllegalStateException.class)
                .hasMessage(FAILURE).hasMessageNotContaining(EMAIL).hasMessageNotContaining(PASSWORD)
                .hasCause(null)
                .satisfies(error -> assertThat(error.getSuppressed()).isEmpty());
    }
}
