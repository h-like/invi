package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.common.ForbiddenException;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.StorageUnavailableException;
import com.invi.api.common.TestEntityIds;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private ObjectProvider<S3Presigner> presignerProvider;
    @Mock private ObjectProvider<S3Client> s3ClientProvider;
    @Mock private S3Presigner s3Presigner;
    @Mock private S3Client s3Client;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService =
                new MediaService(
                        mediaAssetRepository,
                        invitationRepository,
                        presignerProvider,
                        s3ClientProvider,
                        "invi-media",
                        "https://media.example.com");
    }

    private static Member ownerWithId(UUID id) {
        Member owner =
                Member.builder().provider(AuthProvider.KAKAO).providerId("owner").email("o@b.com").name("O").build();
        TestEntityIds.setId(owner, id);
        return owner;
    }

    private static Invitation ownedInvitation(UUID ownerId, UUID invitationId) {
        Invitation invitation =
                Invitation.builder().slug("s").weddingDate(LocalDate.now()).member(ownerWithId(ownerId)).build();
        TestEntityIds.setId(invitation, invitationId);
        return invitation;
    }

    @Test
    void presignUpload_throwsStorageUnavailable_whenR2NotConfigured() {
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(ownedInvitation(ownerId, invitationId)));
        when(presignerProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(
                        () ->
                                mediaService.presignUpload(
                                        ownerId,
                                        invitationId,
                                        new PresignUploadRequest("photo.jpg", "image/jpeg", MediaType.GALLERY, 1024L)))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void presignUpload_throwsForbidden_whenCallerIsNotOwner() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(ownedInvitation(UUID.randomUUID(), invitationId)));

        assertThatThrownBy(
                        () ->
                                mediaService.presignUpload(
                                        UUID.randomUUID(),
                                        invitationId,
                                        new PresignUploadRequest("photo.jpg", "image/jpeg", MediaType.GALLERY, 1024L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void presignUpload_returnsUrlAndKey_whenConfigured() throws MalformedURLException {
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(ownedInvitation(ownerId, invitationId)));
        when(presignerProvider.getIfAvailable()).thenReturn(s3Presigner);

        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://r2.example.com/invi-media/some-key?sig=abc").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PresignUploadResponse response =
                mediaService.presignUpload(
                        ownerId, invitationId, new PresignUploadRequest("photo.svg/../../evil", "image/jpeg", MediaType.GALLERY, 1024L));

        ArgumentCaptor<PutObjectPresignRequest> signed = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(signed.capture());
        assertThat(signed.getValue().putObjectRequest().contentLength()).isEqualTo(1024L);
        assertThat(signed.getValue().putObjectRequest().contentType()).isEqualTo("image/jpeg");
        assertThat(response.uploadUrl()).isEqualTo("https://r2.example.com/invi-media/some-key?sig=abc");
        assertThat(response.r2Key()).startsWith("invitations/" + invitationId + "/").endsWith(".jpg");
        assertThat(response.publicUrl()).isEqualTo("https://media.example.com/" + response.r2Key());
    }

    @Test
    void attach_savesMediaAsset_whenOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(ownedInvitation(ownerId, invitationId)));
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        String key = "invitations/" + invitationId + "/" + UUID.randomUUID() + ".jpg";
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(1024L).contentType("image/jpeg").eTag("etag").build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),
                        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
        MediaAssetDto dto =
                mediaService.attach(
                        ownerId, invitationId, new AttachMediaRequest(key, MediaType.GALLERY, 2));

        assertThat(dto.type()).isEqualTo(MediaType.GALLERY);
        assertThat(dto.sortOrder()).isEqualTo(2);
        assertThat(dto.url()).isEqualTo("https://media.example.com/" + key);
        ArgumentCaptor<HeadObjectRequest> head = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(head.capture());
        assertThat(head.getValue().bucket()).isEqualTo("invi-media");
        assertThat(head.getValue().key()).isEqualTo(key);
        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(get.capture());
        assertThat(get.getValue().range()).isEqualTo("bytes=0-11");
        assertThat(get.getValue().ifMatch()).isEqualTo("etag");
    }

    @Test
    void list_returnsAssetsOrderedBySortOrder_whenOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(ownedInvitation(ownerId, invitationId)));
        Invitation invitation = ownedInvitation(ownerId, invitationId);
        MediaAsset asset = MediaAsset.builder().invitation(invitation).r2Key("k").type(MediaType.GALLERY).sortOrder(0).build();
        when(mediaAssetRepository.findByInvitationIdOrderBySortOrderAsc(invitationId)).thenReturn(List.of(asset));

        List<MediaAssetDto> result = mediaService.list(ownerId, invitationId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url()).isEqualTo("https://media.example.com/k");
    }

    @Test
    void delete_removesAssetAndCallsS3_whenS3ClientAvailable() {
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Invitation invitation = ownedInvitation(ownerId, invitationId);
        MediaAsset asset = MediaAsset.builder().invitation(invitation).r2Key("k").type(MediaType.GALLERY).build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(mediaAssetRepository.findById(mediaId)).thenReturn(Optional.of(asset));
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);

        mediaService.delete(ownerId, invitationId, mediaId);

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        verify(mediaAssetRepository).delete(asset);
    }

    @Test
    void delete_skipsS3Call_whenS3ClientNotConfigured() {
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Invitation invitation = ownedInvitation(ownerId, invitationId);
        MediaAsset asset = MediaAsset.builder().invitation(invitation).r2Key("k").type(MediaType.GALLERY).build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(mediaAssetRepository.findById(mediaId)).thenReturn(Optional.of(asset));
        when(s3ClientProvider.getIfAvailable()).thenReturn(null);

        mediaService.delete(ownerId, invitationId, mediaId);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(mediaAssetRepository).delete(asset);
    }

    @Test
    void delete_throwsNotFound_whenAssetBelongsToDifferentInvitation() {
        UUID ownerId = UUID.randomUUID();
        UUID actualInvitationId = UUID.randomUUID();
        UUID otherInvitationId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Invitation actualInvitation = ownedInvitation(ownerId, actualInvitationId);
        Invitation otherInvitation = ownedInvitation(ownerId, otherInvitationId);
        MediaAsset asset = MediaAsset.builder().invitation(actualInvitation).r2Key("k").type(MediaType.GALLERY).build();
        when(invitationRepository.findById(otherInvitationId)).thenReturn(Optional.of(otherInvitation));
        when(mediaAssetRepository.findById(mediaId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> mediaService.delete(ownerId, otherInvitationId, mediaId))
                .isInstanceOf(NotFoundException.class);
    }
    private final UUID validationOwner = UUID.randomUUID();
    private final UUID validationInvitation = UUID.randomUUID();

    private void givenOwnedInvitation() {
        when(invitationRepository.findById(validationInvitation))
                .thenReturn(Optional.of(ownedInvitation(validationOwner, validationInvitation)));
    }

    private String validKey() {
        return "invitations/" + validationInvitation + "/" + UUID.randomUUID() + ".jpg";
    }

    private void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void presign_rejectsMissingLength() {
        givenOwnedInvitation();
        assertBadRequest(() -> mediaService.presignUpload(validationOwner, validationInvitation,
                new PresignUploadRequest("x", "image/jpeg", MediaType.COVER, null)));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 31457281})
    void presign_rejectsInvalidLength(long length) {
        givenOwnedInvitation();
        assertBadRequest(() -> mediaService.presignUpload(validationOwner, validationInvitation,
                new PresignUploadRequest("x", "image/jpeg", MediaType.COVER, length)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/heic", "image/svg+xml", "image/gif", "text/html", ""})
    void presign_rejectsUnsupportedOutputMime(String mime) {
        givenOwnedInvitation();
        assertBadRequest(() -> mediaService.presignUpload(validationOwner, validationInvitation,
                new PresignUploadRequest("x", mime, MediaType.COVER, 1L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/relative", "ftp://media.example.com", "https://", "https://media.example.com?q=x"})
    void presign_rejectsInvalidPublicUrl(String baseUrl) {
        givenOwnedInvitation();
        MediaService service = new MediaService(mediaAssetRepository, invitationRepository,
                presignerProvider, s3ClientProvider, "invi-media", baseUrl);
        assertThatThrownBy(() -> service.presignUpload(validationOwner, validationInvitation,
                new PresignUploadRequest("x", "image/jpeg", MediaType.COVER, 1L)))
                .isInstanceOf(StorageUnavailableException.class);
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void attach_rejectsOtherInvitationAndMalformedKeys() {
        givenOwnedInvitation();
        for (String key : List.of("invitations/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".jpg",
                "invitations/" + validationInvitation + "/../evil.jpg",
                "invitations/" + validationInvitation + "/not-a-uuid.jpg", validKey() + "/extra",
                validKey().replace(".jpg", ".svg"))) {
            assertBadRequest(() -> mediaService.attach(validationOwner, validationInvitation,
                    new AttachMediaRequest(key, MediaType.COVER, 0)));
        }
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void attach_requiresStorageClient() {
        givenOwnedInvitation();
        assertThatThrownBy(() -> mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(validKey(), MediaType.COVER, 0)))
                .isInstanceOf(StorageUnavailableException.class);
        verify(mediaAssetRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 31457281})
    void attach_rejectsActualInvalidObjectSize(long size) {
        givenOwnedInvitation();
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(size).contentType("image/jpeg").build());
        assertBadRequest(() -> mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(validKey(), MediaType.COVER, 0)));
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/heic", "text/html", ""})
    void attach_rejectsWrongObjectMime(String mime) {
        givenOwnedInvitation();
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(10L).contentType(mime).build());
        assertBadRequest(() -> mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(validKey(), MediaType.COVER, 0)));
    }

    @Test
    void attach_rejectsMissingObject() {
        givenOwnedInvitation();
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        assertBadRequest(() -> mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(validKey(), MediaType.COVER, 0)));
    }

    @Test
    void attach_reportsStorageOutage() {
        givenOwnedInvitation();
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(SdkClientException.create("offline"));
        assertThatThrownBy(() -> mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(validKey(), MediaType.COVER, 0)))
                .isInstanceOf(StorageUnavailableException.class);
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void attach_rejectsSpoofedImageBytes() {
        givenOwnedInvitation();
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(20L).contentType("image/jpeg").build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {1, 2, 3}));
        assertBadRequest(() -> mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(validKey(), MediaType.COVER, 0)));
    }
    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    void attach_acceptsAllowedSignaturesAtSizeLimit(String mime) {
        givenOwnedInvitation();
        String extension = mime.equals("image/jpeg") ? ".jpg" : mime.equals("image/png") ? ".png" : ".webp";
        String key = validKey().replace(".jpg", extension);
        byte[] bytes = mime.equals("image/jpeg") ? new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}
                : mime.equals("image/png") ? new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}
                : new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(MediaService.MAX_UPLOAD_BYTES)
                        .contentType(mime).eTag("etag").build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        MediaAssetDto dto = mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(key, MediaType.COVER, 0));
        assertThat(dto.type()).isEqualTo(MediaType.COVER);
        assertThat(dto.url()).endsWith(extension);
    }

    @Test
    void attach_preservesOwnershipCheckBeforeStorage() {
        when(invitationRepository.findById(validationInvitation))
                .thenReturn(Optional.of(ownedInvitation(UUID.randomUUID(), validationInvitation)));
        assertThatThrownBy(() -> mediaService.attach(validationOwner, validationInvitation,
                new AttachMediaRequest(validKey(), MediaType.COVER, 0))).isInstanceOf(ForbiddenException.class);
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void presign_signsSizeLimitWithRealSdk() {
        givenOwnedInvitation();
        try (S3Presigner signer = S3Presigner.builder()
                .endpointOverride(URI.create("https://r2.example.com"))
                .region(software.amazon.awssdk.regions.Region.of("auto"))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test-key", "test-secret")))
                .build()) {
            when(presignerProvider.getIfAvailable()).thenReturn(signer);
            PresignUploadResponse response = mediaService.presignUpload(validationOwner, validationInvitation,
                    new PresignUploadRequest("x.heic", "image/webp", MediaType.COVER, MediaService.MAX_UPLOAD_BYTES));
            assertThat(response.r2Key()).endsWith(".webp");
            assertThat(response.uploadUrl()).contains("content-length").contains("content-type");
        }
    }
}
