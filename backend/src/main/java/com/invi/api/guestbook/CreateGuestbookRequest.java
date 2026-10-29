package com.invi.api.guestbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Limits mirror frontend/src/blocks/guestLimits.ts so guests see them before submitting. */
public record CreateGuestbookRequest(
        @NotBlank @Size(max = CreateGuestbookRequest.AUTHOR_MAX, message = "이름은 30자 이하로 입력해 주세요.")
                String author,
        @NotBlank @Size(max = CreateGuestbookRequest.MESSAGE_MAX, message = "메시지는 500자 이하로 입력해 주세요.")
                String message) {

    public static final int AUTHOR_MAX = 30;
    public static final int MESSAGE_MAX = 500;
}
