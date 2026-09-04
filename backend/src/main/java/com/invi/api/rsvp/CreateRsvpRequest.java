package com.invi.api.rsvp;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRsvpRequest(
        @NotBlank String guestName,
        @NotNull Boolean attending,
        @NotNull @Min(0) Integer guestCount,
        String message) {
}
