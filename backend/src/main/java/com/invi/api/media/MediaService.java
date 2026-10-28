package com.invi.api.media;

import com.invi.api.common.ForbiddenException;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.StorageUnavailableException;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Uploads never touch our backend — the SPA PUTs bytes directly to the presigned R2 url,
 * then calls attach() to record the resulting key. See CLAUDE.md's media section.
 */
@Service
public class MediaService {

    static final long MAX_UPLOAD_BYTES = 30L * 1024 * 1024;

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
        String extension = extensionFor(request.contentType());
        if (request.contentLength() == null || request.contentLength() <= 0
                || request.contentLength() > MAX_UPLOAD_BYTES) {
            throw badRequest("이미지 크기는 1바이트 이상 30MiB 이하여야 합니다.");
        }
        requirePublicBaseUrl();
        S3Presigner presigner = presignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new StorageUnavailableException("이미지 저장소가 아직 설정되지 않았습니다 (invi.r2.* 설정 필요).");
        }

        String key = "invitations/%s/%s%s".formatted(invitationId, UUID.randomUUID(), extension);

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(UPLOAD_URL_TTL)
                        .putObjectRequest(
                                PutObjectRequest.builder()
                                        .bucket(bucket)
                                        .key(key)
                                        .contentType(request.contentType())
                                        .contentLength(request.contentLength())
                                        .build())
                        .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return new PresignUploadResponse(presigned.url().toString(), key, publicUrl(key));
    }

    @Transactional
    public MediaAssetDto attach(UUID memberId, UUID invitationId, AttachMediaRequest request) {
        Invitation invitation = getOwnedOrThrow(memberId, invitationId);
        String expectedContentType = contentTypeForKey(invitationId, request.r2Key());
        requirePublicBaseUrl();
        verifyUploadedObject(request.r2Key(), expectedContentType);
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
        return publicBaseUrl.isBlank() ? "/" + key : publicBaseUrl.replaceAll("/+$", "") + "/" + key;
    }

    private MediaAssetDto toDto(MediaAsset asset) {
        return new MediaAssetDto(asset.getId(), publicUrl(asset.getR2Key()), asset.getType(), asset.getSortOrder());
    }

    private void requirePublicBaseUrl() {
        try {
            URI uri = URI.create(publicBaseUrl);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || bucket.isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new StorageUnavailableException("이미지 저장소의 버킷과 공개 HTTP(S) URL 설정이 필요합니다.");
        }
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            throw badRequest("JPEG, PNG, WebP 이미지만 업로드할 수 있습니다.");
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw badRequest("JPEG, PNG, WebP 이미지만 업로드할 수 있습니다.");
        };
    }

    private static String contentTypeForKey(UUID invitationId, String key) {
        String prefix = "invitations/" + invitationId + "/";
        if (key == null || !key.startsWith(prefix)
                || !key.substring(prefix.length()).matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(jpg|png|webp)")) {
            throw badRequest("이 청첩장에 발급된 이미지 키가 아닙니다.");
        }
        return key.endsWith(".jpg") ? "image/jpeg" : key.endsWith(".png") ? "image/png" : "image/webp";
    }

    private void verifyUploadedObject(String key, String expectedContentType) {
        S3Client client = s3ClientProvider.getIfAvailable();
        if (client == null) {
            throw new StorageUnavailableException("이미지 저장소가 아직 설정되지 않았습니다 (invi.r2.* 설정 필요).");
        }
        try {
            HeadObjectResponse head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            if (head.contentLength() == null || head.contentLength() <= 0 || head.contentLength() > MAX_UPLOAD_BYTES) {
                throw badRequest("업로드된 이미지 크기는 1바이트 이상 30MiB 이하여야 합니다.");
            }
            if (!expectedContentType.equals(head.contentType())) {
                throw badRequest("업로드된 이미지 형식이 발급된 키와 일치하지 않습니다.");
            }
            // Bounded magic-byte check only, not a full decoder or malware scan.
            // Keys are namespace-validated, not persisted presign receipts. A still-valid PUT URL
            // can overwrite the object after attach; immutable promotion needs a separate flow.
            // If-Match ensures these bytes correspond to the HEAD metadata.
            byte[] header = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key)
                    .range("bytes=0-11").ifMatch(head.eTag()).build()).asByteArray();
            if (!hasImageSignature(header, expectedContentType)) {
                throw badRequest("유효한 JPEG, PNG, WebP 이미지가 아닙니다.");
            }
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404 || ex.statusCode() == 412 || ex.statusCode() == 416) {
                throw badRequest("업로드된 이미지를 찾을 수 없거나 이미지가 변경되었습니다. 다시 업로드해 주세요.");
            }
            throw new StorageUnavailableException("이미지 저장소에 접근할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        } catch (SdkException ex) {
            throw new StorageUnavailableException("이미지 저장소에 접근할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static boolean hasImageSignature(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                    && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10
                    && bytes[6] == 26 && bytes[7] == 10;
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

