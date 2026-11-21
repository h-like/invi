package com.invi.api.invitation;

import com.invi.api.account.CurrentMember;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping
    public List<InvitationDto> listMine(Authentication authentication) {
        return invitationService.findAllOwnedBy(currentMember.requireId(authentication));
    }

    @GetMapping("/{id}")
    public InvitationDto get(Authentication authentication, @PathVariable UUID id) {
        return invitationService.findById(currentMember.requireId(authentication), id);
    }

    @PutMapping("/{id}/page-data")
    public InvitationDto updatePageData(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdatePageDataRequest request) {
        return invitationService.updatePageData(currentMember.requireId(authentication), id, request.pageData(), request.version());
    }

    @PutMapping("/{id}/wedding-date")
    public InvitationDto updateWeddingDate(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateWeddingDateRequest request) {
        return invitationService.updateWeddingDate(currentMember.requireId(authentication), id, request.weddingDate());
    }

    @PostMapping("/{id}/publish")
    public InvitationDto publish(Authentication authentication, @PathVariable UUID id) {
        return invitationService.publish(currentMember.requireId(authentication), id);
    }

    @PostMapping("/{id}/unpublish")
    public InvitationDto unpublish(Authentication authentication, @PathVariable UUID id) {
        return invitationService.unpublish(currentMember.requireId(authentication), id);
    }

    @PostMapping("/{id}/review-link")
    public ReviewLinkDto issueReviewLink(Authentication authentication, @PathVariable UUID id) {
        return invitationService.issueReviewLink(currentMember.requireId(authentication), id);
    }

    @DeleteMapping("/{id}/review-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeReviewLink(Authentication authentication, @PathVariable UUID id) {
        invitationService.revokeReviewLink(currentMember.requireId(authentication), id);
    }

    /** Public: whoever holds the owner's review link may read the saved page, never write to it. */
    @GetMapping("/review/{token}")
    public GuestInvitationDto getByReviewToken(@PathVariable String token) {
        return invitationService.findByReviewToken(token);
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
