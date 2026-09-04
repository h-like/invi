package com.invi.api.account;

import com.invi.api.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;

    /**
     * Temporary stand-in for real session lookup — returns the dev member seeded by
     * MemberSeeder so the frontend has a memberId to work with before Kakao/Google OAuth2
     * login lands. Remove once /api/auth/me (or equivalent) exists.
     */
    @GetMapping("/dev")
    public MemberDto dev() {
        Member member =
                memberRepository
                        .findByProviderAndProviderId(AuthProvider.KAKAO, "dev-000001")
                        .orElseThrow(() -> new NotFoundException("개발용 계정이 아직 시드되지 않았습니다."));
        return new MemberDto(member.getId(), member.getEmail(), member.getName());
    }
}
