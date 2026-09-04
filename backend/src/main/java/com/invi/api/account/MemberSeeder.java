package com.invi.api.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds one placeholder member so invitations can be created locally before
 * real Kakao/Google OAuth2 login lands (Phase 1's account module). Once login
 * is wired, memberId will come from the security context instead of the
 * request body, and this seeder can go.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSeeder implements CommandLineRunner {

    private final MemberRepository memberRepository;

    @Override
    public void run(String... args) {
        if (memberRepository.count() > 0) {
            return;
        }
        Member devMember =
                Member.builder()
                        .provider(AuthProvider.KAKAO)
                        .providerId("dev-000001")
                        .email("dev@invi.local")
                        .name("개발용 계정")
                        .build();
        Member saved = memberRepository.save(devMember);
        log.info("Seeded dev member id={}", saved.getId());
    }
}
