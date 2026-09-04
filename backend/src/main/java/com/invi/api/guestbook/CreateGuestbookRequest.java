package com.invi.api.guestbook;

import jakarta.validation.constraints.NotBlank;

public record CreateGuestbookRequest(@NotBlank String author, @NotBlank String message) {
}
