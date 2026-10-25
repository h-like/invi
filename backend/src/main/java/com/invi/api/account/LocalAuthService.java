package com.invi.api.account;

import com.invi.api.common.ConflictException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LocalAuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LocalAuthController.TokenResponse register(LocalAuthController.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email).isPresent()) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
        Member member = Member.builder()
                .provider(AuthProvider.LOCAL).providerId(email).email(email)
                .name(request.name().strip()).passwordHash(passwordEncoder.encode(request.password())).build();
        try {
            member = memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            if (memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email).isPresent()) {
                throw new ConflictException("이미 가입된 이메일입니다.");
            }
            throw ex;
        }
        return new LocalAuthController.TokenResponse(jwtService.issue(member.getId()));
    }

    public LocalAuthController.TokenResponse login(LocalAuthController.LoginRequest request) {
        Member member = memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, normalizeEmail(request.email()))
                .orElse(null);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || member == null || member.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return new LocalAuthController.TokenResponse(jwtService.issue(member.getId()));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
