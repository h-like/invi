package com.invi.api.guestbook;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations/{invitationId}/guestbook")
@RequiredArgsConstructor
public class GuestbookController {

    private final GuestbookService guestbookService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestbookEntryDto submit(
            @PathVariable UUID invitationId, @Valid @RequestBody CreateGuestbookRequest request) {
        return guestbookService.submit(invitationId, request);
    }

    @GetMapping
    public List<GuestbookEntryDto> list(@PathVariable UUID invitationId) {
        return guestbookService.findByInvitation(invitationId);
    }

    @DeleteMapping("/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID invitationId, @PathVariable UUID entryId) {
        guestbookService.delete(invitationId, entryId);
    }
}
