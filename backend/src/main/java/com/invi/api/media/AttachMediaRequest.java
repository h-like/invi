package com.invi.api.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AttachMediaRequest(@NotBlank String r2Key, @NotNull MediaType type, int sortOrder) {
}
