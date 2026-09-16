package com.invi.api.media;

import com.invi.api.account.CurrentMember;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations/{invitationId}/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final CurrentMember currentMember;

    @PostMapping("/presign")
    public PresignUploadResponse presign(
            Authentication authentication,
            @PathVariable UUID invitationId,
            @Valid @RequestBody PresignUploadRequest request) {
        return mediaService.presignUpload(currentMember.requireId(authentication), invitationId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaAssetDto attach(
            Authentication authentication,
            @PathVariable UUID invitationId,
            @Valid @RequestBody AttachMediaRequest request) {
        return mediaService.attach(currentMember.requireId(authentication), invitationId, request);
    }

    @GetMapping
    public List<MediaAssetDto> list(Authentication authentication, @PathVariable UUID invitationId) {
        return mediaService.list(currentMember.requireId(authentication), invitationId);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID invitationId, @PathVariable UUID mediaId) {
        mediaService.delete(currentMember.requireId(authentication), invitationId, mediaId);
    }
}
