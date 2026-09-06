package com.invi.api.guestbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.common.ForbiddenException;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.TestEntityIds;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuestbookServiceTest {

    @Mock private GuestbookEntryRepository guestbookEntryRepository;
    @Mock private InvitationRepository invitationRepository;

    private GuestbookService guestbookService;

    @BeforeEach
    void setUp() {
        guestbookService = new GuestbookService(guestbookEntryRepository, invitationRepository);
    }

    private static Member ownerWithId(UUID id) {
        Member owner = Member.builder().provider(AuthProvider.KAKAO).providerId("owner").email("o@b.com").name("O").build();
        TestEntityIds.setId(owner, id);
        return owner;
    }

    @Test
    void submit_savesEntry_whenInvitationExists() {
        UUID invitationId = UUID.randomUUID();
        Invitation invitation = Invitation.builder().slug("s").weddingDate(LocalDate.now()).build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(guestbookEntryRepository.save(any(GuestbookEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        GuestbookEntryDto result =
                guestbookService.submit(invitationId, new CreateGuestbookRequest("친구1", "축하해!"));

        assertThat(result.author()).isEqualTo("친구1");
        assertThat(result.message()).isEqualTo("축하해!");
    }

    @Test
    void submit_throwsNotFound_whenInvitationMissing() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> guestbookService.submit(invitationId, new CreateGuestbookRequest("A", "B")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_removesEntry_whenCallerIsOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Invitation invitation =
                Invitation.builder().slug("s").weddingDate(LocalDate.now()).member(ownerWithId(ownerId)).build();
        TestEntityIds.setId(invitation, invitationId);
        GuestbookEntry entry =
                GuestbookEntry.builder().invitation(invitation).author("A").message("B").build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(guestbookEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        guestbookService.delete(ownerId, invitationId, entryId);

        assertThat(entry.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_throwsForbidden_whenCallerIsNotInvitationOwner() {
        UUID invitationId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Invitation invitation =
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .member(ownerWithId(UUID.randomUUID()))
                        .build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> guestbookService.delete(UUID.randomUUID(), invitationId, entryId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_throwsNotFound_whenEntryBelongsToDifferentInvitation() {
        UUID ownerId = UUID.randomUUID();
        UUID actualInvitationId = UUID.randomUUID();
        UUID otherInvitationId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        Invitation actualInvitation =
                Invitation.builder().slug("s").weddingDate(LocalDate.now()).member(ownerWithId(ownerId)).build();
        TestEntityIds.setId(actualInvitation, actualInvitationId);
        Invitation otherInvitation =
                Invitation.builder().slug("t").weddingDate(LocalDate.now()).member(ownerWithId(ownerId)).build();
        TestEntityIds.setId(otherInvitation, otherInvitationId);
        GuestbookEntry entry =
                GuestbookEntry.builder().invitation(actualInvitation).author("A").message("B").build();

        when(invitationRepository.findById(otherInvitationId)).thenReturn(Optional.of(otherInvitation));
        when(guestbookEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> guestbookService.delete(ownerId, otherInvitationId, entryId))
                .isInstanceOf(NotFoundException.class);
    }
}
