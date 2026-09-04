package com.invi.api.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.invi.api.common.NotFoundException;
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

    private RsvpService rsvpService;

    @BeforeEach
    void setUp() {
        rsvpService = new RsvpService(rsvpRepository, invitationRepository);
    }

    @Test
    void submit_savesRsvp_whenInvitationExists() {
        UUID invitationId = UUID.randomUUID();
        Invitation invitation = Invitation.builder().slug("s").weddingDate(LocalDate.now()).build();
        CreateRsvpRequest request = new CreateRsvpRequest("홍길동", true, 2, "축하합니다");

        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(rsvpRepository.save(any(Rsvp.class))).thenAnswer(inv -> inv.getArgument(0));

        RsvpDto result = rsvpService.submit(invitationId, request);

        assertThat(result.guestName()).isEqualTo("홍길동");
        assertThat(result.attending()).isTrue();
        assertThat(result.guestCount()).isEqualTo(2);
    }

    @Test
    void submit_throwsNotFound_whenInvitationMissing() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> rsvpService.submit(invitationId, new CreateRsvpRequest("A", true, 1, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByInvitation_returnsMappedDtos() {
        UUID invitationId = UUID.randomUUID();
        Rsvp rsvp =
                Rsvp.builder()
                        .invitation(Invitation.builder().slug("s").weddingDate(LocalDate.now()).build())
                        .guestName("김철수")
                        .attending(false)
                        .guestCount(0)
                        .build();
        when(rsvpRepository.findByInvitationId(invitationId)).thenReturn(List.of(rsvp));

        List<RsvpDto> result = rsvpService.findByInvitation(invitationId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).guestName()).isEqualTo("김철수");
    }
}
