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
                                        new PresignUploadRequest("photo.jpg", "image/jpeg", MediaType.GALLERY)))
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
                                        new PresignUploadRequest("photo.jpg", "image/jpeg", MediaType.GALLERY)))
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
                        ownerId, invitationId, new PresignUploadRequest("photo.jpg", "image/jpeg", MediaType.GALLERY));

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

        MediaAssetDto dto =
                mediaService.attach(
                        ownerId, invitationId, new AttachMediaRequest("invitations/x/y.jpg", MediaType.GALLERY, 2));

        assertThat(dto.type()).isEqualTo(MediaType.GALLERY);
        assertThat(dto.sortOrder()).isEqualTo(2);
        assertThat(dto.url()).isEqualTo("https://media.example.com/invitations/x/y.jpg");
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
}
