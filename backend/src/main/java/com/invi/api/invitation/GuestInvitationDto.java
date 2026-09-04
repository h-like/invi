package com.invi.api.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;

/** Public, read-only shape returned to guests — no owner/member info exposed. */
public record GuestInvitationDto(
        UUID id, String slug, LocalDate weddingDate, JsonNode pageData, JsonNode langVariants) {
}
