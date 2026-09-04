package com.invi.api.invitation;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDate;
import java.util.UUID;

/** Public, read-only shape returned to guests — no owner/member info exposed. */
public record GuestInvitationDto(
        UUID id,
        String slug,
        LocalDate weddingDate,
        @JsonRawValue String pageData,
        @JsonRawValue String langVariants) {
}
