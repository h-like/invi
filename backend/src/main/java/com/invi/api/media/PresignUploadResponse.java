package com.invi.api.media;

/** uploadUrl: PUT the raw file bytes here directly (not through our API). r2Key: pass back to /media to attach it. */
public record PresignUploadResponse(String uploadUrl, String r2Key, String publicUrl) {
}
