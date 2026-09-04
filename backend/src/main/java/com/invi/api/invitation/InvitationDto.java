package com.invi.api.invitation;

import java.time.LocalDate;
import java.util.UUID;

public record InvitationDto(
        UUID id,
        String slug,
        LocalDate weddingDate,
        Plan plan,
        InvitationStatus status) {
}
