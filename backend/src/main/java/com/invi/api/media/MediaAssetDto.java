package com.invi.api.media;

import java.util.UUID;

public record MediaAssetDto(UUID id, String url, MediaType type, int sortOrder) {
}
