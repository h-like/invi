package com.invi.api.invitation;

/** The owner's private review link key; the page lives at {@code /r/{token}}. */
public record ReviewLinkDto(String token) {
}
