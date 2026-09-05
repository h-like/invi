package com.invi.api.account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Maps Kakao/Google's differently-shaped userinfo responses into a Member, creating
 * one on first login. The resulting OAuth2User carries the Member's own id under a
 * synthetic "inviMemberId" attribute so OAuth2LoginSuccessHandler doesn't need a
 * second lookup to issue the JWT.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String MEMBER_ID_ATTRIBUTE = "inviMemberId";

    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        ProviderProfile profile = extractProfile(registrationId, oAuth2User.getAttributes());
        Member member = findOrCreateMember(profile);

        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put(MEMBER_ID_ATTRIBUTE, member.getId().toString());

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, MEMBER_ID_ATTRIBUTE);
    }

    private record ProviderProfile(AuthProvider provider, String providerId, String email, String name) {}

    @SuppressWarnings("unchecked")
    private ProviderProfile extractProfile(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            String providerId = String.valueOf(attributes.get("id"));
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
            Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;
            String name = profile != null ? (String) profile.get("nickname") : null;
            return new ProviderProfile(AuthProvider.KAKAO, providerId, email, name);
        }
        if ("google".equals(registrationId)) {
            String providerId = String.valueOf(attributes.get("sub"));
            String email = (String) attributes.get("email");
            String name = (String) attributes.get("name");
            return new ProviderProfile(AuthProvider.GOOGLE, providerId, email, name);
        }
        throw new OAuth2AuthenticationException("지원하지 않는 로그인 제공자입니다: " + registrationId);
    }

    private Member findOrCreateMember(ProviderProfile profile) {
        return memberRepository
                .findByProviderAndProviderId(profile.provider(), profile.providerId())
                .orElseGet(
                        () ->
                                memberRepository.save(
                                        Member.builder()
                                                .provider(profile.provider())
                                                .providerId(profile.providerId())
                                                .email(fallback(profile.email(), profile))
                                                .name(profile.name() != null ? profile.name() : "사용자")
                                                .build()));
    }

    /** Kakao's account_email scope can come back empty if the user declines consent. */
    private String fallback(String email, ProviderProfile profile) {
        if (email != null) return email;
        return profile.providerId() + "@" + profile.provider().name().toLowerCase() + ".invi.local";
    }
}
