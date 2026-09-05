package com.invi.api.account;

import com.invi.api.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberRepository memberRepository;
    private final CurrentMember currentMember;

    @GetMapping("/me")
    public MemberDto me(Authentication authentication) {
        Member member =
                memberRepository
                        .findById(currentMember.requireId(authentication))
                        .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        return new MemberDto(member.getId(), member.getEmail(), member.getName());
    }
}
