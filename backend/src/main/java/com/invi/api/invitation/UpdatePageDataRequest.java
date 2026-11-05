package com.invi.api.invitation;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * @param version the invitation version the editor's copy was loaded or last saved at. Required:
 *     a client that cannot say which copy it edited must not overwrite the stored one.
 */
public record UpdatePageDataRequest(
        @NotNull JsonNode pageData,
        @NotNull(message = "편집 화면을 새로고침한 뒤 다시 저장해 주세요.") Long version) {
}
