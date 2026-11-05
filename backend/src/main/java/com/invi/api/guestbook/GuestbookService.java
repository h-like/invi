package com.invi.api.guestbook;

import com.invi.api.common.ForbiddenException;
import com.invi.api.common.GuestWriteRateLimiter;
import com.invi.api.common.NotFoundException;
import com.invi.api.invitation.GuestPageView;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import com.invi.api.invitation.InvitationStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestbookService {

    private final GuestbookEntryRepository guestbookEntryRepository;
    private final InvitationRepository invitationRepository;
    private final GuestWriteRateLimiter rateLimiter;

    public GuestbookEntryDto submit(UUID invitationId, CreateGuestbookRequest request, String clientAddress) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        // Guests can only reach published pages; drafts must not collect writes.
                        .filter(i -> i.getStatus() == InvitationStatus.PUBLISHED)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));
        // A hidden guestbook stops new entries; existing ones stay for the couple.
        if (!GuestPageView.hasVisibleBlock(invitation.getPageData(), "guestbook")) {
            throw new ForbiddenException("지금은 방명록을 받지 않고 있습니다.");
        }
        // After the lookup, so unknown ids never create limiter entries.
        rateLimiter.acquire(GuestWriteRateLimiter.Kind.GUESTBOOK, invitationId, clientAddress);

        GuestbookEntry entry =
                GuestbookEntry.builder()
                        .invitation(invitation)
                        .author(request.author())
                        .message(request.message())
                        .build();

        try {
            return toDto(guestbookEntryRepository.save(entry));
        } catch (RuntimeException error) {
            // Nothing was saved, so a retry after e.g. a database cold start must not be throttled.
            rateLimiter.release(GuestWriteRateLimiter.Kind.GUESTBOOK, invitationId, clientAddress);
            throw error;
        }
    }

    /** Public: only a published page's guestbook, and nothing while the couple keeps it hidden. */
    public List<GuestbookEntryDto> findForGuests(UUID invitationId) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        .filter(i -> i.getStatus() == InvitationStatus.PUBLISHED)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));
        if (!GuestPageView.hasVisibleBlock(invitation.getPageData(), "guestbook")) {
            return List.of();
        }
        return entries(invitationId);
    }

    /** Owner-only: every live entry, whether or not the guestbook is currently shown to guests. */
    public List<GuestbookEntryDto> findForOwner(UUID memberId, UUID invitationId) {
        getOwnedOrThrow(memberId, invitationId);
        return entries(invitationId);
    }

    private List<GuestbookEntryDto> entries(UUID invitationId) {
        return guestbookEntryRepository.findByInvitationIdAndDeletedAtIsNull(invitationId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void delete(UUID memberId, UUID invitationId, UUID entryId) {
        getOwnedOrThrow(memberId, invitationId);

        GuestbookEntry entry =
                guestbookEntryRepository
                        .findById(entryId)
                        .orElseThrow(() -> new NotFoundException("방명록을 찾을 수 없습니다: " + entryId));
        if (!entry.getInvitation().getId().equals(invitationId)) {
            throw new NotFoundException("방명록을 찾을 수 없습니다: " + entryId);
        }
        entry.softDelete();
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

    private GuestbookEntryDto toDto(GuestbookEntry entry) {
        return new GuestbookEntryDto(entry.getId(), entry.getAuthor(), entry.getMessage());
    }
}
