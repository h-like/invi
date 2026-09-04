package com.invi.api.guestbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.invi.api.common.NotFoundException;
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
    void delete_throwsNotFound_whenEntryBelongsToDifferentInvitation() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID actualInvitationId = UUID.randomUUID();
        UUID otherInvitationId = UUID.randomUUID();
        Invitation actualInvitation = Invitation.builder().slug("s").weddingDate(LocalDate.now()).build();
        setId(actualInvitation, actualInvitationId);
        GuestbookEntry entry =
                GuestbookEntry.builder().invitation(actualInvitation).author("A").message("B").build();
        when(guestbookEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> guestbookService.delete(otherInvitationId, entryId))
                .isInstanceOf(NotFoundException.class);
    }

    /** BaseEntity's id is only ever assigned by Hibernate on persist, so tests that need a
     * stable id on an unmanaged entity (like the ownership check above) set it via reflection. */
    private static void setId(Object entity, UUID id) throws Exception {
        var field = com.invi.api.common.BaseEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
