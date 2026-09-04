package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.account.Member;
import com.invi.api.account.MemberRepository;
import com.invi.api.common.ConflictException;
import com.invi.api.common.NotFoundException;
import com.invi.api.template.Template;
import com.invi.api.template.TemplateRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private TemplateRepository templateRepository;

    private InvitationService invitationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(invitationRepository, memberRepository, templateRepository);
    }

    @Test
    void create_savesInvitation_withPageDataCopiedFromTemplateDefault() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        JsonNode defaultPageData = objectMapper.readTree("{\"blocks\":[]}");
        Member member = Member.builder().provider(com.invi.api.account.AuthProvider.KAKAO).providerId("1").email("a@b.com").name("A").build();
        Template template = Template.builder().name("클래식").category("classic").defaultPageData(defaultPageData).build();
        CreateInvitationRequest request = new CreateInvitationRequest(memberId, templateId, "our-wedding", LocalDate.now().plusMonths(1));

        when(invitationRepository.existsBySlug("our-wedding")).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        InvitationDto result = invitationService.create(request);

        assertThat(result.slug()).isEqualTo("our-wedding");
        assertThat(result.status()).isEqualTo(InvitationStatus.DRAFT);
        assertThat(result.plan()).isEqualTo(Plan.FREE);
        assertThat(result.pageData()).isEqualTo(defaultPageData);
        assertThat(result.pageData()).isNotSameAs(defaultPageData);
    }

    @Test
    void create_throwsConflict_whenSlugAlreadyTaken() {
        CreateInvitationRequest request =
                new CreateInvitationRequest(UUID.randomUUID(), UUID.randomUUID(), "taken", LocalDate.now());
        when(invitationRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> invitationService.create(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void findPublishedBySlug_throwsNotFound_whenInvitationIsStillDraft() {
        Invitation draft = Invitation.builder().slug("draft-wedding").weddingDate(LocalDate.now()).build();
        when(invitationRepository.findBySlug("draft-wedding")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> invitationService.findPublishedBySlug("draft-wedding"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findPublishedBySlug_returnsGuestView_whenPublished() {
        Invitation published =
                Invitation.builder()
                        .slug("our-wedding")
                        .weddingDate(LocalDate.now())
                        .status(InvitationStatus.PUBLISHED)
                        .build();
        when(invitationRepository.findBySlug("our-wedding")).thenReturn(Optional.of(published));

        GuestInvitationDto result = invitationService.findPublishedBySlug("our-wedding");

        assertThat(result.slug()).isEqualTo("our-wedding");
    }

    @Test
    void publish_flipsStatusToPublished() {
        UUID id = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation invitation =
                Invitation.builder().slug("s").weddingDate(LocalDate.now()).template(template).build();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        InvitationDto result = invitationService.publish(id);

        assertThat(result.status()).isEqualTo(InvitationStatus.PUBLISHED);
    }
}
