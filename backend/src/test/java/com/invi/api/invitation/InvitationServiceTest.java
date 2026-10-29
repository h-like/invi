package com.invi.api.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.account.AuthProvider;
import com.invi.api.account.Member;
import com.invi.api.account.MemberRepository;
import com.invi.api.common.ConflictException;
import com.invi.api.common.ForbiddenException;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.TestEntityIds;
import com.invi.api.media.MediaLifecycle;
import com.invi.api.template.Template;
import com.invi.api.template.TemplateRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final String MEDIA_A =
            "invitations/11111111-1111-1111-1111-111111111111/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    private static final String MEDIA_B =
            "invitations/11111111-1111-1111-1111-111111111111/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.png";
    private static final String MEDIA_C =
            "invitations/11111111-1111-1111-1111-111111111111/cccccccc-cccc-4ccc-8ccc-cccccccccccc.webp";
    private static final String MEDIA_D =
            "invitations/11111111-1111-1111-1111-111111111111/dddddddd-dddd-4ddd-8ddd-dddddddddddd.jpg";

    @Mock private InvitationRepository invitationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private MediaLifecycle lifecycle;

    private InvitationService invitationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        invitationService =
                new InvitationService(invitationRepository, memberRepository, templateRepository, objectMapper, lifecycle);
    }

    private static Member ownerWithId(UUID id) {
        Member owner = Member.builder().provider(AuthProvider.KAKAO).providerId("owner").email("o@b.com").name("O").build();
        TestEntityIds.setId(owner, id);
        return owner;
    }

    @Test
    void create_savesInvitation_withPageDataCopiedFromTemplateDefault() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        JsonNode defaultPageData = objectMapper.readTree(
                "{\"blocks\":[],\"unknownMetadata\":{\"photo\":\"https://cdn.example/" + MEDIA_A + "\"}}");
        Member member = Member.builder().provider(AuthProvider.KAKAO).providerId("1").email("a@b.com").name("A").build();
        Template template = Template.builder().name("클래식").category("classic").defaultPageData(defaultPageData).build();
        CreateInvitationRequest request = new CreateInvitationRequest(templateId, "our-wedding", LocalDate.now().plusMonths(1));

        when(invitationRepository.existsBySlug("our-wedding")).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        InvitationDto result = invitationService.create(memberId, request);

        assertThat(result.slug()).isEqualTo("our-wedding");
        assertThat(result.status()).isEqualTo(InvitationStatus.DRAFT);
        assertThat(result.plan()).isEqualTo(Plan.FREE);
        assertThat(result.pageData()).isEqualTo(defaultPageData.toString());

        ArgumentCaptor<Invitation> saved = ArgumentCaptor.forClass(Invitation.class);
        InOrder order = inOrder(lifecycle, invitationRepository, memberRepository, templateRepository);
        order.verify(lifecycle).lock();
        order.verify(invitationRepository).existsBySlug("our-wedding");
        order.verify(memberRepository).findById(memberId);
        order.verify(templateRepository).findById(templateId);
        order.verify(lifecycle).referencesChanged(Set.of(), Set.of(MEDIA_A));
        order.verify(invitationRepository).save(saved.capture());
        assertThat(saved.getValue().getPageData()).isEqualTo(defaultPageData).isNotSameAs(defaultPageData);
        assertThat(saved.getValue().getPageData().get("unknownMetadata"))
                .isNotSameAs(defaultPageData.get("unknownMetadata"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) saved.getValue().getPageData().get("unknownMetadata"))
                .put("photo", "changed");
        assertThat(defaultPageData.at("/unknownMetadata/photo").asText()).contains(MEDIA_A);
    }

    @Test
    void create_throwsConflict_whenSlugAlreadyTaken() {
        CreateInvitationRequest request = new CreateInvitationRequest(UUID.randomUUID(), "taken", LocalDate.now());
        when(invitationRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> invitationService.create(UUID.randomUUID(), request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findById_returnsInvitation_whenCallerIsOwner() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation invitation =
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(ownerId))
                        .build();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        InvitationDto result = invitationService.findById(ownerId, id);

        assertThat(result.slug()).isEqualTo("s");
        verifyNoInteractions(lifecycle);
    }

    @Test
    void findById_throwsForbidden_whenCallerIsNotOwner() {
        UUID id = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation invitation =
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(UUID.randomUUID()))
                        .build();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.findById(UUID.randomUUID(), id))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updatePageData_bridgesJackson3RequestNodeIntoJackson2EntityField() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation invitation = spy(
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(ownerId))
                        .build());
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        tools.jackson.databind.JsonNode incoming =
                tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"blocks\":[{\"id\":\"hero-1\"}]}");

        InvitationDto result = invitationService.updatePageData(ownerId, id, incoming);

        assertThat(result.pageData()).isEqualTo("{\"blocks\":[{\"id\":\"hero-1\"}]}");
        InOrder order = inOrder(lifecycle, invitationRepository, invitation);
        order.verify(lifecycle).lock();
        order.verify(invitationRepository).findById(id);
        order.verify(lifecycle).referencesChanged(Set.of(), Set.of());
        order.verify(invitation).updatePageData(any(JsonNode.class));
    }

    @Test
    void updatePageData_throwsForbidden_whenCallerIsNotOwner() {
        UUID id = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation invitation =
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(UUID.randomUUID()))
                        .build();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));
        tools.jackson.databind.JsonNode incoming =
                tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"blocks\":[]}");

        assertThatThrownBy(() -> invitationService.updatePageData(UUID.randomUUID(), id, incoming))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void findAllOwnedBy_returnsOnlyTheCallersInvitations() {
        UUID ownerId = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation newer =
                Invitation.builder()
                        .slug("newer")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(ownerId))
                        .build();
        Invitation older =
                Invitation.builder()
                        .slug("older")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(ownerId))
                        .build();
        when(invitationRepository.findAllByMemberIdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of(newer, older));

        List<InvitationDto> result = invitationService.findAllOwnedBy(ownerId);

        assertThat(result).extracting(InvitationDto::slug).containsExactly("newer", "older");
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
    void publish_flipsStatusToPublished() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation invitation = spy(
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(ownerId))
                        .pageData(objectMapper.readTree("{\"image\":\"" + MEDIA_A + "\"}"))
                        .langVariants(objectMapper.readTree("{\"en\":{\"image\":\"" + MEDIA_B + "\"}}"))
                        .build());
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        InvitationDto result = invitationService.publish(ownerId, id);

        assertThat(result.status()).isEqualTo(InvitationStatus.PUBLISHED);
        InOrder order = inOrder(lifecycle, invitationRepository, invitation);
        order.verify(lifecycle).lock();
        order.verify(invitationRepository).findById(id);
        order.verify(lifecycle).assertUsable(Set.of(MEDIA_A, MEDIA_B));
        order.verify(invitation).publish();
    }

    @Test
    void updatePageData_preservesUnknownFieldsAndLanguageReferencesInBothSets() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        JsonNode oldPage = objectMapper.readTree(
                "{\"blocks\":[],\"future\":{\"shared\":\"" + MEDIA_A + "\",\"removed\":\"" + MEDIA_D + "\"}}");
        JsonNode languages = objectMapper.readTree(
                "{\"en\":{\"future\":{\"shared\":\"" + MEDIA_A + "\",\"languageOnly\":\"" + MEDIA_B + "\"}}}");
        Invitation invitation = Invitation.builder()
                .member(ownerWithId(ownerId))
                .template(Template.builder().name("클래식").category("classic").build())
                .slug("s")
                .weddingDate(LocalDate.now())
                .pageData(oldPage)
                .langVariants(languages)
                .build();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));
        tools.jackson.databind.JsonNode incoming = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
                "{\"blocks\":[],\"future\":{\"photo\":\"https://cdn.example/" + MEDIA_C
                        + "\",\"metadata\":{\"keep\":true}},\"extra\":[1,2]}");
        doAnswer(call -> {
            assertThat(invitation.getPageData()).isSameAs(oldPage);
            assertThat(invitation.getLangVariants()).isSameAs(languages);
            return null;
        }).when(lifecycle).referencesChanged(Set.of(MEDIA_A, MEDIA_B, MEDIA_D), Set.of(MEDIA_A, MEDIA_B, MEDIA_C));

        InvitationDto result = invitationService.updatePageData(ownerId, id, incoming);

        verify(lifecycle).referencesChanged(Set.of(MEDIA_A, MEDIA_B, MEDIA_D), Set.of(MEDIA_A, MEDIA_B, MEDIA_C));
        assertThat(invitation.getPageData()).isEqualTo(objectMapper.readTree(incoming.toString()));
        assertThat(result.pageData()).isEqualTo(incoming.toString());
        assertThat(invitation.getLangVariants()).isSameAs(languages);
        assertThat(result.langVariants()).isEqualTo(languages.toString());
    }

    @Test
    void updatePageData_rejectsDeletionFencedMediaWithoutChangingPageOrPublishedStatus() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        JsonNode oldPage = objectMapper.readTree("{\"image\":\"" + MEDIA_A + "\"}");
        JsonNode languages = objectMapper.readTree("{\"en\":{\"image\":\"" + MEDIA_B + "\"}}");
        Invitation invitation = spy(Invitation.builder()
                .member(ownerWithId(ownerId))
                .template(Template.builder().name("클래식").category("classic").build())
                .slug("s")
                .weddingDate(LocalDate.now())
                .pageData(oldPage)
                .langVariants(languages)
                .status(InvitationStatus.PUBLISHED)
                .build());
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));
        tools.jackson.databind.JsonNode incoming = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree("{\"unknownField\":\"" + MEDIA_C + "\"}");
        doThrow(new ConflictException("deletion fence")).when(lifecycle)
                .referencesChanged(Set.of(MEDIA_A, MEDIA_B), Set.of(MEDIA_B, MEDIA_C));

        assertThatThrownBy(() -> invitationService.updatePageData(ownerId, id, incoming))
                .isInstanceOf(ConflictException.class);

        assertThat(invitation.getPageData()).isSameAs(oldPage);
        assertThat(invitation.getLangVariants()).isSameAs(languages);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PUBLISHED);
        verify(invitation, never()).updatePageData(any(JsonNode.class));
        verify(invitationRepository, never()).save(any(Invitation.class));
        InOrder order = inOrder(lifecycle, invitationRepository);
        order.verify(lifecycle).lock();
        order.verify(invitationRepository).findById(id);
        order.verify(lifecycle).referencesChanged(Set.of(MEDIA_A, MEDIA_B), Set.of(MEDIA_B, MEDIA_C));
    }

    @Test
    void publish_rejectsDeletionFencedLanguageMediaWithoutChangingDraftOrPage() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        JsonNode oldPage = objectMapper.readTree("{\"image\":\"" + MEDIA_A + "\"}");
        JsonNode languages = objectMapper.readTree("{\"en\":{\"futureImage\":\"" + MEDIA_B + "\"}}");
        Invitation invitation = spy(Invitation.builder()
                .member(ownerWithId(ownerId))
                .template(Template.builder().name("클래식").category("classic").build())
                .slug("s")
                .weddingDate(LocalDate.now())
                .pageData(oldPage)
                .langVariants(languages)
                .build());
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));
        doThrow(new ConflictException("deletion fence")).when(lifecycle).assertUsable(Set.of(MEDIA_A, MEDIA_B));

        assertThatThrownBy(() -> invitationService.publish(ownerId, id)).isInstanceOf(ConflictException.class);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DRAFT);
        assertThat(invitation.getPageData()).isSameAs(oldPage);
        assertThat(invitation.getLangVariants()).isSameAs(languages);
        verify(invitation, never()).publish();
        InOrder order = inOrder(lifecycle, invitationRepository);
        order.verify(lifecycle).lock();
        order.verify(invitationRepository).findById(id);
        order.verify(lifecycle).assertUsable(Set.of(MEDIA_A, MEDIA_B));
    }

    @Test
    void create_rejectsDeletionFencedTemplateCopyBeforeSaving() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        JsonNode defaults = objectMapper.readTree("{\"unknownPhoto\":\"" + MEDIA_A + "\"}");
        Template template = Template.builder().name("클래식").category("classic").defaultPageData(defaults).build();
        CreateInvitationRequest request = new CreateInvitationRequest(templateId, "copy", LocalDate.now());
        when(memberRepository.findById(ownerId)).thenReturn(Optional.of(ownerWithId(ownerId)));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        doThrow(new ConflictException("deletion fence")).when(lifecycle).referencesChanged(Set.of(), Set.of(MEDIA_A));

        assertThatThrownBy(() -> invitationService.create(ownerId, request)).isInstanceOf(ConflictException.class);

        verify(invitationRepository, never()).save(any(Invitation.class));
        assertThat(template.getDefaultPageData()).isSameAs(defaults);
        assertThat(defaults.get("unknownPhoto").asText()).isEqualTo(MEDIA_A);
        InOrder order = inOrder(lifecycle, invitationRepository, memberRepository, templateRepository);
        order.verify(lifecycle).lock();
        order.verify(invitationRepository).existsBySlug("copy");
        order.verify(memberRepository).findById(ownerId);
        order.verify(templateRepository).findById(templateId);
        order.verify(lifecycle).referencesChanged(Set.of(), Set.of(MEDIA_A));
    }

    @Test
    void publish_throwsForbidden_whenCallerIsNotOwner() {
        UUID id = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        Invitation invitation =
                Invitation.builder()
                        .slug("s")
                        .weddingDate(LocalDate.now())
                        .template(template)
                        .member(ownerWithId(UUID.randomUUID()))
                        .build();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.publish(UUID.randomUUID(), id))
                .isInstanceOf(ForbiddenException.class);
    }
}
