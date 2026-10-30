package com.invi.api.invitation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Logged as-is: ids only, never slugs or names (slugs often contain the couple's names). */
public record InvitationExpiryReport(boolean dryRun, LocalDate weddingDateBefore, List<UUID> expired, int deleted,
        List<UUID> failed) {}
