package com.invi.api.rsvp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Limits mirror frontend/src/blocks/guestLimits.ts so guests see them before submitting. */
public record CreateRsvpRequest(
        @NotBlank @Size(max = CreateRsvpRequest.NAME_MAX, message = "이름은 30자 이하로 입력해 주세요.")
                String guestName,
        @NotNull Boolean attending,
        @NotNull @Min(0) @Max(value = CreateRsvpRequest.GUEST_COUNT_MAX, message = "인원은 20명 이하로 입력해 주세요.")
                Integer guestCount,
        @Size(max = CreateRsvpRequest.MESSAGE_MAX, message = "전달할 말은 200자 이하로 입력해 주세요.")
                String message) {

    public static final int NAME_MAX = 30;
    public static final int GUEST_COUNT_MAX = 20;
    public static final int MESSAGE_MAX = 200;
}
