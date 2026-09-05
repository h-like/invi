package com.invi.api.invitation;

import com.invi.api.account.CurrentMember;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final CurrentMember currentMember;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationDto create(Authentication authentication, @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.create(currentMember.requireId(authentication), request);
    }

    @GetMapping("/{id}")
    public InvitationDto get(@PathVariable UUID id) {
        return invitationService.findById(id);
    }

    @PutMapping("/{id}/page-data")
    public InvitationDto updatePageData(
            @PathVariable UUID id, @Valid @RequestBody UpdatePageDataRequest request) {
        return invitationService.updatePageData(id, request.pageData());
    }

    @PostMapping("/{id}/publish")
    public InvitationDto publish(@PathVariable UUID id) {
        return invitationService.publish(id);
    }

    @GetMapping("/slug/{slug}")
    public GuestInvitationDto getBySlug(@PathVariable String slug) {
        return invitationService.findPublishedBySlug(slug);
    }

    @GetMapping("/slug-available")
    public SlugAvailability slugAvailable(@RequestParam String slug) {
        return new SlugAvailability(invitationService.isSlugAvailable(slug));
    }

    public record SlugAvailability(boolean available) {}
}
