package com.invi.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.invi.api.common.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private MemberRepository memberRepository;
    @Mock private Authentication authentication;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(memberRepository, new CurrentMember());
    }

    @Test
    void me_returnsTheAuthenticatedMember() {
        UUID memberId = UUID.randomUUID();
        Member member = Member.builder().provider(AuthProvider.GOOGLE).providerId("1").email("a@b.com").name("A").build();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(memberId.toString());
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        MemberDto result = controller.me(authentication);

        assertThat(result.email()).isEqualTo("a@b.com");
        assertThat(result.name()).isEqualTo("A");
    }

    @Test
    void me_throwsNotFound_whenMemberWasDeletedAfterTokenWasIssued() {
        UUID memberId = UUID.randomUUID();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(memberId.toString());
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.me(authentication)).isInstanceOf(NotFoundException.class);
    }
}
