package com.invi.api.account;

import java.util.UUID;

public record MemberDto(UUID id, String email, String name) {
}
