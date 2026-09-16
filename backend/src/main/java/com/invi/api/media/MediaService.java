package com.invi.api.media;

import com.invi.api.common.ForbiddenException;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.StorageUnavailableException;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Uploads never touch our backend — the SPA PUTs bytes directly to the presigned R2 url,
 * then calls attach() to record the resulting key. See CLAUDE.md's media section.
 */
@Service
public class MediaService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(5);

    private final MediaAssetRepository mediaAssetRepository;
    private final InvitationRepository invitationRepository;
    private final ObjectProvider<S3Presigner> presignerProvider;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final String bucket;
    private final String publicBaseUrl;

    public MediaService(
            MediaAssetRepository mediaAssetRepository,
            InvitationRepository invitationRepository,
            ObjectProvider<S3Presigner> presignerProvider,
            ObjectProvider<S3Client> s3ClientProvider,
            @Value("${invi.r2.bucket:invi-media}") String bucket,
            @Value("${invi.r2.public-base-url:}") String publicBaseUrl) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.invitationRepository = invitationRepository;
        this.presignerProvider = presignerProvider;
        this.s3ClientProvider = s3ClientProvider;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    public PresignUploadResponse presignUpload(UUID memberId, UUID invitationId, PresignUploadRequest request) {
        getOwnedOrThrow(memberId, invitationId);
        S3Presigner presigner = presignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new StorageUnavailableException("이미지 저장소가 아직 설정되지 않았습니다 (invi.r2.* 설정 필요).");
        }

        String key = "invitations/%s/%s%s".formatted(invitationId, UUID.randomUUID(), extensionOf(request.fileName()));

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(UPLOAD_URL_TTL)
                        .putObjectRequest(
                                PutObjectRequest.builder()
                                        .bucket(bucket)
                                        .key(key)
                                        .contentType(request.contentType())
                                        .build())
                        .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return new PresignUploadResponse(presigned.url().toString(), key, publicUrl(key));
    }

    @Transactional
    public MediaAssetDto attach(UUID memberId, UUID invitationId, AttachMediaRequest request) {
        Invitation invitation = getOwnedOrThrow(memberId, invitationId);
        MediaAsset asset =
                MediaAsset.builder()
                        .invitation(invitation)
                        .r2Key(request.r2Key())
                        .type(request.type())
                        .sortOrder(request.sortOrder())
                        .build();
        return toDto(mediaAssetRepository.save(asset));
    }

    public List<MediaAssetDto> list(UUID memberId, UUID invitationId) {
        getOwnedOrThrow(memberId, invitationId);
        return mediaAssetRepository.findByInvitationIdOrderBySortOrderAsc(invitationId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void delete(UUID memberId, UUID invitationId, UUID mediaId) {
        getOwnedOrThrow(memberId, invitationId);
        MediaAsset asset =
                mediaAssetRepository
                        .findById(mediaId)
                        .orElseThrow(() -> new NotFoundException("이미지를 찾을 수 없습니다: " + mediaId));
        if (!asset.getInvitation().getId().equals(invitationId)) {
            throw new NotFoundException("이미지를 찾을 수 없습니다: " + mediaId);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client != null) {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(asset.getR2Key()).build());
        }
        mediaAssetRepository.delete(asset);
    }

    private Invitation getOwnedOrThrow(UUID memberId, UUID invitationId) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));
        if (!invitation.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인 소유의 청첩장만 접근할 수 있습니다.");
        }
        return invitation;
    }

    private String publicUrl(String key) {
        return publicBaseUrl.isBlank() ? "/" + key : publicBaseUrl + "/" + key;
    }

    private MediaAssetDto toDto(MediaAsset asset) {
        return new MediaAssetDto(asset.getId(), publicUrl(asset.getR2Key()), asset.getType(), asset.getSortOrder());
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }
}
