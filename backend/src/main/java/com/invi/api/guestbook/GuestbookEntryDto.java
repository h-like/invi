package com.invi.api.guestbook;

import java.time.Instant;
import java.util.UUID;

public record GuestbookEntryDto(
        UUID id,
        String author,
        String message,
        Instant createdAt) {
}
