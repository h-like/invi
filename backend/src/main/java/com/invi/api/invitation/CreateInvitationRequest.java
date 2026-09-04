package com.invi.api.invitation;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInvitationRequest(
        @NotNull UUID memberId,
        @NotNull UUID templateId,
        @NotBlank
                @Pattern(regexp = "^[a-z0-9-]{3,50}$", message = "영문 소문자, 숫자, 하이픈만 3~50자로 입력하세요")
                String slug,
        @NotNull @FutureOrPresent LocalDate weddingDate) {
}
