package com.invi.api.rsvp;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations/{invitationId}/rsvps")
@RequiredArgsConstructor
public class RsvpController {

    private final RsvpService rsvpService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsvpDto submit(@PathVariable UUID invitationId, @Valid @RequestBody CreateRsvpRequest request) {
        return rsvpService.submit(invitationId, request);
    }

    @GetMapping
    public List<RsvpDto> list(@PathVariable UUID invitationId) {
        return rsvpService.findByInvitation(invitationId);
    }
}
