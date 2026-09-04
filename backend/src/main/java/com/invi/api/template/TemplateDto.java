package com.invi.api.template;

import java.util.UUID;

public record TemplateDto(UUID id, String name, String category, String thumbnailUrl) {
}
