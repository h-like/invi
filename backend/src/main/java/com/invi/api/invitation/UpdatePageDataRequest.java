package com.invi.api.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UpdatePageDataRequest(@NotNull JsonNode pageData) {
}
