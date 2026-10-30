package com.invi.api.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.common.ForbiddenException;
import com.invi.api.common.GuestWriteRateLimiter;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.TooManyRequestsException;
import com.invi.api.common.TestEntityIds;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RsvpServiceTest {

    @Mock private RsvpRepository rsvpRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private GuestWriteRateLimiter rateLimiter;

    private RsvpService rsvpService;

    @BeforeEach
    void setUp() {
        rsvpService = new RsvpService(rsvpRepository, invitationRepository, rateLimiter);
    }

    private static Member ownerWithId(UUID id) {
        Member owner = Member.builder().provider(AuthProvider.KAKAO).providerId("owner").email("o@b.com").name("O").build();
        TestEntityIds.setId(owner, id);
        return owner;
    }

    @Test
    void submit_savesRsvp_whenInvitationExists() {
        UUID invitationId = UUID.randomUUID();
        Invitation invitation = Invitation.builder().slug("s").weddingDate(LocalDate.now()).build();
        CreateRsvpRequest request = new CreateRsvpRequest("홍길동", true, 2, "축하합니다");

        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(rsvpRepository.save(any(Rsvp.class))).thenAnswer(inv -> inv.getArgument(0));

        RsvpDto result = rsvpService.submit(invitationId, request, "203.0.113.7");

        assertThat(result.guestName()).isEqualTo("홍길동");
        assertThat(result.attending()).isTrue();
        assertThat(result.guestCount()).isEqualTo(2);
    }

    @Test
    void submit_throwsNotFound_whenInvitationMissing() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> rsvpService.submit(invitationId, new CreateRsvpRequest("A", true, 1, null), "203.0.113.7"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByInvitation_returnsMappedDtos_whenCallerIsOwner() {
        UUID invitationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Invitation invitation =
                Invitation.builder().slug("s").weddingDate(LocalDate.now()).member(ownerWithId(ownerId)).build();
        Rsvp rsvp =
                Rsvp.builder().invitation(invitation).guestName("김철수").attending(false).guestCount(0).build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(rsvpRepository.findByInvitationId(invitationId)).thenReturn(List.of(rsvp));

        List<RsvpDto> result = rsvpService.findByInvitation(ownerId, invitationId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).guestName()).isEqualTo("김철수");
    }

    @Test
    void findByInvitation_throwsForbidden_whenCallerIsNotOwner() {
        UUID invitationId = UUID.randomUUID();
        Invitation invitation =
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .member(ownerWithId(UUID.randomUUID()))
                        .build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> rsvpService.findByInvitation(UUID.randomUUID(), invitationId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void findByInvitation_throwsNotFound_whenInvitationMissing() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rsvpService.findByInvitation(UUID.randomUUID(), invitationId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void submit_countsAgainstTheRateLimitOnlyForAnExistingInvitation() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rsvpService.submit(invitationId, new CreateRsvpRequest("A", true, 1, null), "203.0.113.7"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void submit_savesNothing_whenRateLimited() {
        UUID invitationId = UUID.randomUUID();
        Invitation invitation = Invitation.builder().slug("s").weddingDate(LocalDate.now()).build();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        doThrow(new TooManyRequestsException(60)).when(rateLimiter)
                .acquire(GuestWriteRateLimiter.Kind.RSVP, invitationId, "203.0.113.7");

        assertThatThrownBy(() -> rsvpService.submit(invitationId, new CreateRsvpRequest("A", true, 1, null), "203.0.113.7"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(rsvpRepository, never()).save(any());
    }
}
