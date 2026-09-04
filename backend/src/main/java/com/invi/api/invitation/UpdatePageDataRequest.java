package com.invi.api.invitation;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UpdatePageDataRequest(@NotNull JsonNode pageData) {
}
