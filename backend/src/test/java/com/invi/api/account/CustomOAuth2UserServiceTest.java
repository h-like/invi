package com.invi.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invi.api.account.CustomOAuth2UserService.ProviderProfile;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private MemberRepository memberRepository;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(memberRepository);
    }

    @Test
    void extractProfile_readsKakaoNestedAccountAndProfile() {
        Map<String, Object> attributes =
                Map.of(
                        "id", 123456789,
                        "kakao_account",
                        Map.of("email", "a@kakao.com", "profile", Map.of("nickname", "민수")));

        ProviderProfile profile = service.extractProfile("kakao", attributes);

        assertThat(profile.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(profile.providerId()).isEqualTo("123456789");
        assertThat(profile.email()).isEqualTo("a@kakao.com");
        assertThat(profile.name()).isEqualTo("민수");
    }

    @Test
    void extractProfile_kakao_hasNullEmailAndName_whenConsentWasDeclined() {
        Map<String, Object> attributes = Map.of("id", 42);

        ProviderProfile profile = service.extractProfile("kakao", attributes);

        assertThat(profile.email()).isNull();
        assertThat(profile.name()).isNull();
        assertThat(profile.providerId()).isEqualTo("42");
    }

    @Test
    void extractProfile_readsGoogleFlatAttributes() {
        Map<String, Object> attributes = Map.of("sub", "google-sub-1", "email", "b@gmail.com", "name", "지영");

        ProviderProfile profile = service.extractProfile("google", attributes);

        assertThat(profile.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(profile.providerId()).isEqualTo("google-sub-1");
        assertThat(profile.email()).isEqualTo("b@gmail.com");
        assertThat(profile.name()).isEqualTo("지영");
    }

    @Test
    void extractProfile_throws_forUnsupportedProvider() {
        assertThatThrownBy(() -> service.extractProfile("naver", Map.of()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void findOrCreateMember_returnsExisting_whenAlreadyRegistered() {
        Member existing = Member.builder().provider(AuthProvider.KAKAO).providerId("1").email("a@b.com").name("A").build();
        ProviderProfile profile = new ProviderProfile(AuthProvider.KAKAO, "1", "a@b.com", "A");
        when(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "1"))
                .thenReturn(Optional.of(existing));

        Member result = service.findOrCreateMember(profile);

        assertThat(result).isSameAs(existing);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void findOrCreateMember_createsNewMember_whenNotYetRegistered() {
        ProviderProfile profile = new ProviderProfile(AuthProvider.GOOGLE, "sub-1", "b@gmail.com", "지영");
        when(memberRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member result = service.findOrCreateMember(profile);

        assertThat(result.getEmail()).isEqualTo("b@gmail.com");
        assertThat(result.getName()).isEqualTo("지영");
    }

    @Test
    void findOrCreateMember_fallsBackToSyntheticEmail_whenProviderGaveNone() {
        ProviderProfile profile = new ProviderProfile(AuthProvider.KAKAO, "999", null, null);
        when(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "999")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member result = service.findOrCreateMember(profile);

        assertThat(result.getEmail()).isEqualTo("999@kakao.invi.local");
        assertThat(result.getName()).isEqualTo("사용자");
    }
}
