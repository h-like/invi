package com.invi.api.invitation;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Same rule as creation; the free-plan expiry batch counts from this date. */
public record UpdateWeddingDateRequest(@NotNull @FutureOrPresent LocalDate weddingDate) {
}
