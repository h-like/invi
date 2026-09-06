package com.invi.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class CurrentMemberTest {

    private final CurrentMember currentMember = new CurrentMember();

    @Test
    void requireId_throwsAccessDenied_whenAuthenticationIsNull() {
        assertThatThrownBy(() -> currentMember.requireId(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireId_throwsAccessDenied_whenNotAuthenticated(@Mock Authentication authentication) {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThatThrownBy(() -> currentMember.requireId(authentication))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireId_returnsMemberId_whenAuthenticated(@Mock Authentication authentication) {
        UUID memberId = UUID.randomUUID();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(memberId.toString());

        assertThat(currentMember.requireId(authentication)).isEqualTo(memberId);
    }
}
