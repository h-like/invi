package com.invi.api.rsvp;

import com.invi.api.common.ForbiddenException;
import com.invi.api.common.NotFoundException;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RsvpService {

    private final RsvpRepository rsvpRepository;
    private final InvitationRepository invitationRepository;

    public RsvpDto submit(UUID invitationId, CreateRsvpRequest request) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));

        Rsvp rsvp =
                Rsvp.builder()
                        .invitation(invitation)
                        .guestName(request.guestName())
                        .attending(request.attending())
                        .guestCount(request.guestCount())
                        .message(request.message())
                        .build();

        return toDto(rsvpRepository.save(rsvp));
    }

    /** RSVP 응답 열람은 기획서 5번대로 관리자(청첩장 소유자) 전용이다. */
    public List<RsvpDto> findByInvitation(UUID memberId, UUID invitationId) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));
        if (!invitation.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인 소유의 청첩장만 접근할 수 있습니다.");
        }
        return rsvpRepository.findByInvitationId(invitationId).stream().map(this::toDto).toList();
    }

    private RsvpDto toDto(Rsvp rsvp) {
        return new RsvpDto(
                rsvp.getId(), rsvp.getGuestName(), rsvp.getAttending(), rsvp.getGuestCount(), rsvp.getMessage());
    }
}
