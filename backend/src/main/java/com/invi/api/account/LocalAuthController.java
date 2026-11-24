package com.invi.api.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class LocalAuthController {
    private final LocalAuthService localAuthService;
    private final boolean registrationEnabled;

    public LocalAuthController(
            LocalAuthService localAuthService,
            @Value("${invi.auth.registration-enabled:true}") boolean registrationEnabled) {
        this.localAuthService = localAuthService;
        this.registrationEnabled = registrationEnabled;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Signup is disabled.");
        }
        return localAuthService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return localAuthService.login(request);
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 50) String name,
            /** The terms and privacy policy were shown and accepted; signup is refused without it. */
            // Boxed so a missing field reaches validation with this message instead of a parse error.
            @NotNull(message = "이용약관과 개인정보처리방침에 동의해 주세요.")
            @AssertTrue(message = "이용약관과 개인정보처리방침에 동의해 주세요.") Boolean agreedToPolicies) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 64) String password) {}

    public record TokenResponse(String token) {}
}
