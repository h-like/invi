package com.invi.api.invitation;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDate;
import java.util.UUID;

public record InvitationDto(
        UUID id,
        String slug,
        UUID templateId,
        LocalDate weddingDate,
        Plan plan,
        InvitationStatus status,
        @JsonRawValue String pageData,
        @JsonRawValue String langVariants) {
}
