package com.invi.api.account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.invi.api.common.ConflictException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class LocalAuthServiceTest {
    private final MemberRepository repository = mock(MemberRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private LocalAuthService service;

    @BeforeEach
    void setUp() {
        service = new LocalAuthService(repository, encoder, jwt);
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(repository.findByProviderAndProviderId(AuthProvider.LOCAL, "user@example.com")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            assertThat(member.getEmail()).isEqualTo("user@example.com");
            assertThat(member.getName()).isEqualTo("User");
            assertThat(member.getPasswordHash()).isNotEqualTo("password123");
            assertThat(encoder.matches("password123", member.getPasswordHash())).isTrue();
            return member;
        });
        service.register(new LocalAuthController.RegisterRequest(" USER@example.com ", "password123", " User "));
        verify(repository).saveAndFlush(any());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(repository.findByProviderAndProviderId(AuthProvider.LOCAL, "user@example.com"))
                .thenReturn(Optional.of(Member.builder().build()));
        assertThatThrownBy(() -> service.register(new LocalAuthController.RegisterRequest("user@example.com", "password123", "User")))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void loginVerifiesPasswordAndIssuesToken() {
        Member member = Member.builder().passwordHash(encoder.encode("password123")).build();
        when(repository.findByProviderAndProviderId(AuthProvider.LOCAL, "user@example.com")).thenReturn(Optional.of(member));
        when(jwt.issue(member.getId())).thenReturn("token");
        assertThat(service.login(new LocalAuthController.LoginRequest("USER@example.com", "password123")).token()).isEqualTo("token");
    }

    @Test
    void wrongPasswordAndUnknownEmailAreRejected() {
        Member member = Member.builder().passwordHash(encoder.encode("password123")).build();
        when(repository.findByProviderAndProviderId(AuthProvider.LOCAL, "user@example.com")).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.login(new LocalAuthController.LoginRequest("user@example.com", "incorrect")))
                .isInstanceOf(ResponseStatusException.class);
        when(repository.findByProviderAndProviderId(AuthProvider.LOCAL, "user@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LocalAuthController.LoginRequest("user@example.com", "password123")))
                .isInstanceOf(ResponseStatusException.class);
        verify(jwt, never()).issue(any(UUID.class));
    }

    @Test
    void oversizedUtf8PasswordIsRejectedBeforeHashing() {
        assertThatThrownBy(() -> service.register(new LocalAuthController.RegisterRequest("user@example.com", "가".repeat(25), "User")))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
