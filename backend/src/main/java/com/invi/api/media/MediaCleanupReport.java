package com.invi.api.media;

/** No keys, signed URLs, credentials or other invitation details are exposed. */
public record MediaCleanupReport(boolean dryRun, long graceHours, int scanned, int skippedUnmanaged,
        int tracked, int referenced, int waiting, int eligible, int deleting, int deleted, int failed) {}
