package com.invi.api.rsvp;

import com.invi.api.common.ForbiddenException;
import com.invi.api.common.GuestWriteRateLimiter;
import com.invi.api.common.NotFoundException;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import com.invi.api.invitation.InvitationStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RsvpService {

    private final RsvpRepository rsvpRepository;
    private final InvitationRepository invitationRepository;
    private final GuestWriteRateLimiter rateLimiter;

    public RsvpDto submit(UUID invitationId, CreateRsvpRequest request, String clientAddress) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        // Guests can only reach published pages; drafts must not collect writes.
                        .filter(i -> i.getStatus() == InvitationStatus.PUBLISHED)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));
        // After the lookup, so unknown ids never create limiter entries.
        rateLimiter.acquire(GuestWriteRateLimiter.Kind.RSVP, invitationId, clientAddress);

        Rsvp rsvp =
                Rsvp.builder()
                        .invitation(invitation)
                        .guestName(request.guestName())
                        .attending(request.attending())
                        .guestCount(request.guestCount())
                        .message(request.message())
                        .build();

        try {
            return toDto(rsvpRepository.save(rsvp));
        } catch (RuntimeException error) {
            // Nothing was saved, so a retry after e.g. a database cold start must not be throttled.
            rateLimiter.release(GuestWriteRateLimiter.Kind.RSVP, invitationId, clientAddress);
            throw error;
        }
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
