package com.invi.api.rsvp;

import java.util.UUID;

public record RsvpDto(
        UUID id,
        String guestName,
        Boolean attending,
        Integer guestCount,
        String message) {
}
