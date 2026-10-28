package com.invi.api.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull MediaType type,
        @NotNull Long contentLength) {
}
