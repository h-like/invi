package com.invi.api.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;

public record InvitationDto(
        UUID id,
        String slug,
        UUID templateId,
        LocalDate weddingDate,
        Plan plan,
        InvitationStatus status,
        JsonNode pageData,
        JsonNode langVariants) {
}
