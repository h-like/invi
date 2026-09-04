package com.invi.api.invitation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.account.Member;
import com.invi.api.account.MemberRepository;
import com.invi.api.common.ConflictException;
import com.invi.api.common.NotFoundException;
import com.invi.api.template.Template;
import com.invi.api.template.TemplateRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final TemplateRepository templateRepository;

    /** Jackson 2 — matches Hibernate's JSON column mapping. See JacksonConfig. */
    private final ObjectMapper entityJsonMapper;

    @Transactional
    public InvitationDto create(CreateInvitationRequest request) {
        if (invitationRepository.existsBySlug(request.slug())) {
            throw new ConflictException("이미 사용 중인 슬러그입니다: " + request.slug());
        }

        Member member =
                memberRepository
                        .findById(request.memberId())
                        .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + request.memberId()));
        Template template =
                templateRepository
                        .findById(request.templateId())
                        .orElseThrow(() -> new NotFoundException("템플릿을 찾을 수 없습니다: " + request.templateId()));

        Invitation invitation =
                Invitation.builder()
                        .member(member)
                        .template(template)
                        .slug(request.slug())
                        .weddingDate(request.weddingDate())
                        .pageData(template.getDefaultPageData() != null ? template.getDefaultPageData().deepCopy() : null)
                        .build();

        return toDto(invitationRepository.save(invitation));
    }

    public InvitationDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    public GuestInvitationDto findPublishedBySlug(String slug) {
        Invitation invitation =
                invitationRepository
                        .findBySlug(slug)
                        .filter(i -> i.getStatus() == InvitationStatus.PUBLISHED)
                        .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + slug));

        return new GuestInvitationDto(
                invitation.getId(),
                invitation.getSlug(),
                invitation.getWeddingDate(),
                toRawJson(invitation.getPageData()),
                toRawJson(invitation.getLangVariants()));
    }

    public boolean isSlugAvailable(String slug) {
        return !invitationRepository.existsBySlug(slug);
    }

    @Transactional
    public InvitationDto updatePageData(UUID id, tools.jackson.databind.JsonNode pageData) {
        Invitation invitation = getOrThrow(id);
        invitation.updatePageData(toEntityJson(pageData));
        return toDto(invitation);
    }

    /** Jackson 3 (HTTP request) -> Jackson 2 (entity/Hibernate) via the JSON text they both agree on. */
    private JsonNode toEntityJson(tools.jackson.databind.JsonNode pageData) {
        try {
            return entityJsonMapper.readTree(pageData.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A JsonNode's own toString() should always be valid JSON", e);
        }
    }

    @Transactional
    public InvitationDto publish(UUID id) {
        Invitation invitation = getOrThrow(id);
        invitation.publish();
        return toDto(invitation);
    }

    private Invitation getOrThrow(UUID id) {
        return invitationRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("청첩장을 찾을 수 없습니다: " + id));
    }

    private InvitationDto toDto(Invitation invitation) {
        return new InvitationDto(
                invitation.getId(),
                invitation.getSlug(),
                invitation.getTemplate().getId(),
                invitation.getWeddingDate(),
                invitation.getPlan(),
                invitation.getStatus(),
                toRawJson(invitation.getPageData()),
                toRawJson(invitation.getLangVariants()));
    }

    private static String toRawJson(JsonNode node) {
        return node != null ? node.toString() : null;
    }
}
