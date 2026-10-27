package com.invi.api.template;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.util.UUID;

public record TemplateDetailDto(
        UUID id, String name, String category, String thumbnailUrl, @JsonRawValue String defaultPageData) {
}
