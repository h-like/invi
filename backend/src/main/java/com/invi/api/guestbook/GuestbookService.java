package com.invi.api.guestbook;

import com.invi.api.common.ForbiddenException;
import com.invi.api.common.NotFoundException;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
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

    public GuestbookEntryDto submit(UUID invitationId, CreateGuestbookRequest request) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));

        GuestbookEntry entry =
                GuestbookEntry.builder()
                        .invitation(invitation)
                        .author(request.author())
                        .message(request.message())
                        .build();

        return toDto(guestbookEntryRepository.save(entry));
    }

    public List<GuestbookEntryDto> findByInvitation(UUID invitationId) {
        return guestbookEntryRepository.findByInvitationIdAndDeletedAtIsNull(invitationId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void delete(UUID memberId, UUID invitationId, UUID entryId) {
        Invitation invitation =
                invitationRepository
                        .findById(invitationId)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + invitationId));
        if (!invitation.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인 소유의 청첩장만 접근할 수 있습니다.");
        }

        GuestbookEntry entry =
                guestbookEntryRepository
                        .findById(entryId)
                        .orElseThrow(() -> new NotFoundException("방명록을 찾을 수 없습니다: " + entryId));
        if (!entry.getInvitation().getId().equals(invitationId)) {
            throw new NotFoundException("방명록을 찾을 수 없습니다: " + entryId);
        }
        entry.softDelete();
    }

    private GuestbookEntryDto toDto(GuestbookEntry entry) {
        return new GuestbookEntryDto(entry.getId(), entry.getAuthor(), entry.getMessage());
    }
}
