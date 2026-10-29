package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import com.invi.api.invitation.InvitationStatus;
import com.invi.api.template.Template;
import com.invi.api.template.TemplateRepository;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaReferencesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INVITATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String JPG = key(1, "jpg");
    private static final String PNG = key(2, "png");
    private static final String WEBP = key(3, "webp");

    @Mock private InvitationRepository invitationRepository;
    @Mock private TemplateRepository templateRepository;

    private MediaReferences references;

    @BeforeEach
    void setUp() {
        references = new MediaReferences(invitationRepository, templateRepository);
    }

    @Test
    void allKeys_includesDraftPublishedArchivedHiddenVariantsAndEveryTemplate() {
        ObjectNode hiddenPage = MAPPER.createObjectNode().put("visible", false).put("futureImage", JPG);
        ObjectNode variants = MAPPER.createObjectNode();
        variants.putObject("unrecognized-language").putArray("unknown").add(PNG);
        ObjectNode publishedPage = MAPPER.createObjectNode().put("image", WEBP);
        String archivedKey = key(4, "jpg");
        String templateKey = key(5, "png");
        String thumbnailKey = key(6, "webp");
        String otherTemplateKey = key(7, "jpg");
        Invitation draft = Invitation.builder().status(InvitationStatus.DRAFT)
                .pageData(hiddenPage).langVariants(variants).build();
        Invitation published = Invitation.builder().status(InvitationStatus.PUBLISHED)
                .pageData(publishedPage).build();
        Invitation archived = Invitation.builder().status(InvitationStatus.ARCHIVED)
                .pageData(MAPPER.createObjectNode().put("oldField", archivedKey)).build();
        Template first = Template.builder()
                .defaultPageData(MAPPER.createObjectNode().put(templateKey, false))
                .thumbnailUrl("https://old.example/cdn/prefix/" + thumbnailKey + "?v=1#photo").build();
        Template second = Template.builder()
                .defaultPageData(MAPPER.createArrayNode().add(otherTemplateKey)).build();
        when(invitationRepository.findAll()).thenReturn(List.of(draft, published, archived));
        when(templateRepository.findAll()).thenReturn(List.of(first, second));
        JsonNode pageBefore = hiddenPage.deepCopy();
        JsonNode variantsBefore = variants.deepCopy();

        assertThat(references.allKeys()).containsExactlyInAnyOrder(
                JPG, PNG, WEBP, archivedKey, templateKey, thumbnailKey, otherTemplateKey);
        assertThat(hiddenPage).isEqualTo(pageBefore);
        assertThat(variants).isEqualTo(variantsBefore);
        verify(invitationRepository).findAll();
        verify(templateRepository).findAll();
        verifyNoMoreInteractions(invitationRepository, templateRepository);
    }

    @Test
    void allKeys_acceptsAbsentJsonAndThumbnailFields() {
        when(invitationRepository.findAll()).thenReturn(List.of(Invitation.builder().build()));
        when(templateRepository.findAll()).thenReturn(List.of(Template.builder().build()));

        assertThat(references.allKeys()).isEmpty();
    }

    @Test
    void allKeys_acceptsEmptyRepositories() {
        when(invitationRepository.findAll()).thenReturn(List.of());
        when(templateRepository.findAll()).thenReturn(List.of());

        assertThat(references.allKeys()).isEmpty();
    }

    @Test
    void allKeys_propagatesInvitationReadFailureInsteadOfReturningPartialReferences() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(invitationRepository.findAll()).thenThrow(failure);

        assertThatThrownBy(references::allKeys).isSameAs(failure);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void allKeys_propagatesTemplateReadFailureAfterInvitationScan() {
        when(invitationRepository.findAll()).thenReturn(List.of(Invitation.builder()
                .pageData(MAPPER.createObjectNode().put("image", JPG)).build()));
        IllegalStateException failure = new IllegalStateException("template read failed");
        when(templateRepository.findAll()).thenThrow(failure);

        assertThatThrownBy(references::allKeys).isSameAs(failure);
    }

    @Test
    void allKeys_propagatesJsonScanFailure() {
        JsonNode broken = mock(JsonNode.class);
        IllegalStateException failure = new IllegalStateException("cannot scan saved JSON");
        when(broken.isTextual()).thenThrow(failure);
        when(invitationRepository.findAll()).thenReturn(List.of(Invitation.builder().pageData(broken).build()));

        assertThatThrownBy(references::allKeys).isSameAs(failure);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void extract_scansUnknownNestedArraysHiddenSectionsAndFieldNames() {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode hidden = root.putArray("futureSections").addObject().put("visible", false);
        hidden.putArray("unrecognized").addArray().add(JPG).addObject().put("asset", PNG);
        hidden.put("https://old.example/" + WEBP, false);

        assertThat(MediaReferences.extract(root)).containsExactlyInAnyOrder(JPG, PNG, WEBP);
    }

    @Test
    void extract_scansTextRootAndArrayRoot() {
        assertThat(MediaReferences.extract(MAPPER.getNodeFactory().textNode(JPG))).containsExactly(JPG);
        assertThat(MediaReferences.extract(MAPPER.createArrayNode().add(PNG).add(WEBP)))
                .containsExactlyInAnyOrder(PNG, WEBP);
    }

    @Test
    void extract_acceptsNullAndNonTextScalars() {
        assertThat(MediaReferences.extract(null)).isEmpty();
        assertThat(MediaReferences.extract(NullNode.getInstance())).isEmpty();
        assertThat(MediaReferences.extract(MAPPER.createArrayNode().addNull().add(false).add(42))).isEmpty();
    }

    @Test
    void extract_doesNotMutateJsonOrRemoveUnknownContent() {
        ObjectNode root = MAPPER.createObjectNode().put("unknown", "background: url('" + JPG + "')");
        root.putArray("preserve").addNull().add(42).addObject().put(PNG, "future metadata");
        JsonNode before = root.deepCopy();

        MediaReferences.extract(root);

        assertThat(root).isEqualTo(before);
    }

    @Test
    void extract_hasNoDepthCutoffOrRecursiveStackLimit() {
        ArrayNode root = MAPPER.createArrayNode();
        ArrayNode current = root;
        for (int depth = 0; depth < 20000; depth++) {
            current = current.addArray();
        }
        current.add(JPG);

        assertThat(MediaReferences.extract(root)).containsExactly(JPG);
    }

    @Test
    void extract_hasNoNodeCountCutoff() {
        ArrayNode root = MAPPER.createArrayNode();
        for (int index = 0; index < 20000; index++) {
            root.add("future content");
        }
        root.add(WEBP);

        assertThat(MediaReferences.extract(root)).containsExactly(WEBP);
    }

    @Test
    void extract_propagatesTraversalErrors() {
        JsonNode broken = mock(JsonNode.class);
        IllegalStateException failure = new IllegalStateException("field traversal failed");
        when(broken.isObject()).thenReturn(true);
        when(broken.fields()).thenThrow(failure);

        assertThatThrownBy(() -> MediaReferences.extract(broken)).isSameAs(failure);
    }

    @Test
    void extractText_findsRawKeysAndDeduplicates() {
        assertThat(MediaReferences.extractText(JPG + "\n" + PNG + " " + JPG + " " + WEBP))
                .containsExactlyInAnyOrder(JPG, PNG, WEBP);
    }

    @Test
    void extractText_ignoresHostAndFindsCdnPrefixesQueriesAndFragments() {
        String text = "https://old.r2.dev/" + JPG + "?width=100#cover "
                + "https://new.example/cdn/cache/v9/" + PNG + "?token=x "
                + "https://future.example/render?asset=" + WEBP + "#photo";

        assertThat(MediaReferences.extractText(text)).containsExactlyInAnyOrder(JPG, PNG, WEBP);
    }

    @Test
    void extractText_findsHtmlCssAndPlainFutureText() {
        String text = "<img src=\"https://old.example/" + JPG + "\">"
                + "background-image:url('/prefix/" + PNG + "');\nfuture field: [" + WEBP + "]";

        assertThat(MediaReferences.extractText(text)).containsExactlyInAnyOrder(JPG, PNG, WEBP);
    }

    @Test
    void extractText_decodesOnceAndTwiceEncodedKeysAndMixedRawText() {
        String once = percentEncode(PNG);
        String twice = percentEncode(percentEncode(WEBP));

        assertThat(MediaReferences.extractText(JPG + " https://cdn.example/prefix/" + once + "?v=1 " + twice))
                .containsExactlyInAnyOrder(JPG, PNG, WEBP);
    }

    @Test
    void extractText_decodesLowercaseEscapesAndEncodedObjectFieldNames() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put(percentEncode(JPG).toLowerCase(Locale.ROOT), false);

        assertThat(MediaReferences.extract(root)).containsExactly(JPG);
    }

    @Test
    void extractText_keepsFindingValidKeysAroundMalformedPercentSequences() {
        String text = "% %QZ %2 %GG+" + percentEncode(JPG) + "%broken " + PNG + "%";

        assertThat(MediaReferences.extractText(text)).containsExactlyInAnyOrder(JPG, PNG);
    }

    @Test
    void extractText_boundsPercentDecodingToTwoPasses() {
        String tripleEncoded = percentEncode(percentEncode(percentEncode(JPG)));

        assertThat(MediaReferences.extractText(tripleEncoded)).isEmpty();
    }

    @Test
    void extractText_acceptsNullAndEmptyAndDoesNotInventReferences() {
        assertThat(MediaReferences.extractText(null)).isEmpty();
        assertThat(MediaReferences.extractText("")).isEmpty();
        assertThat(MediaReferences.extractText("https://example.com/stock/photo.jpg %GG")).isEmpty();
    }

    @Test
    void extractText_preservesCaseSensitiveR2Identity() {
        assertThat(MediaReferences.extractText(JPG.toUpperCase(Locale.ROOT))).isEmpty();
        assertThat(MediaReferences.extractText(JPG.replace("12d3", "12D3"))).isEmpty();
        assertThat(MediaReferences.extractText(JPG.replace(".jpg", ".JPG"))).isEmpty();
    }

    @Test
    void extractText_conservativelyProtectsSubstringsWithoutAuthorizingTheirDeletion() {
        String foreignPath = "foreign/" + JPG + ".backup";

        assertThat(MediaReferences.extractText(foreignPath)).containsExactly(JPG);
        assertThat(MediaReferences.isManagedKey(foreignPath)).isFalse();
    }

    @Test
    void isManagedKey_acceptsAllSupportedExtensionsInvitationVersionsAndUploadVariants() {
        assertThat(MediaReferences.isManagedKey(JPG)).isTrue();
        assertThat(MediaReferences.isManagedKey(PNG)).isTrue();
        assertThat(MediaReferences.isManagedKey(WEBP)).isTrue();
        for (char version : "0123456789abcdef".toCharArray()) {
            String invitationId = "123e4567-e89b-" + version + "2d3-a456-426614174000";
            assertThat(MediaReferences.isManagedKey(JPG.replace(INVITATION_ID, invitationId))).isTrue();
        }
        for (char variant : "89ab".toCharArray()) {
            assertThat(MediaReferences.isManagedKey(JPG.replace("-8899-", "-" + variant + "899-"))).isTrue();
        }
    }

    @Test
    void isManagedKey_rejectsForeignTraversalSuffixEncodedAndMalformedKeys() {
        List<String> invalid = List.of(
                "", "invitations/not-a-uuid/photo.jpg", JPG.toUpperCase(Locale.ROOT),
                JPG.replace("12d3", "12D3"), JPG.replace("invitations/", "templates/"),
                "/" + JPG, "foreign/" + JPG, "../" + JPG, "https://example.com/" + JPG,
                JPG.replace("/00112233", "/../00112233"), JPG.replace("invitations/", "invitations/./"),
                JPG + "/../other.jpg", JPG + ".backup", JPG + "x", JPG + "?v=1", JPG + "#photo",
                JPG + "\n", JPG + " ", JPG + "/", percentEncode(JPG),
                JPG.replace("-4677-", "-1677-"), JPG.replace("-8899-", "-7899-"),
                JPG.replace("-8899-", "-c899-"), JPG.replace(".jpg", ".jpeg"),
                JPG.replace(".jpg", ".svg"), JPG.replace(".jpg", ".JPG"),
                JPG.replace("123e4567-", "123e456-"), JPG.replace("00112233-", "0011223g-"));

        assertThat(MediaReferences.isManagedKey(null)).isFalse();
        for (String key : invalid) {
            assertThat(MediaReferences.isManagedKey(key)).as("unmanaged key: %s", key).isFalse();
        }
    }

    @Test
    void extractionResultsAreIndependentBetweenCalls() {
        var first = MediaReferences.extractText(JPG);
        first.clear();

        assertThat(MediaReferences.extractText(JPG)).containsExactly(JPG);
        assertThat(MediaReferences.extract(MAPPER.createObjectNode().put("image", PNG))).containsExactly(PNG);
    }

    @Test
    void extractText_resolvesLiteralSelfDotSegmentsInAbsoluteAndRelativePaths() {
        String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/./");

        assertThat(MediaReferences.extractText("https://cdn.example/" + alias)).containsExactly(WEBP);
        assertThat(MediaReferences.extractText("/cdn/prefix/" + alias)).containsExactly(WEBP);
    }

    @Test
    void extractText_resolvesLiteralParentDotSegments() {
        String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/unused/../");

        assertThat(MediaReferences.extractText("https://cdn.example/" + alias)).containsExactly(WEBP);
    }

    @Test
    void extractText_resolvesOnceAndTwicePercentEncodedDotSegments() {
        for (String dot : List.of("%2e", "%2E", "%252e", "%252E")) {
            String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/" + dot + "/");
            assertThat(MediaReferences.extractText("https://cdn.example/" + alias))
                    .as("self segment: %s", dot).containsExactly(WEBP);
        }
        for (String parent : List.of("%2e%2e", ".%2E", "%2e.", "%252e%252E", ".%252e")) {
            String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/unused/" + parent + "/");
            assertThat(MediaReferences.extractText("https://cdn.example/" + alias))
                    .as("parent segment: %s", parent).containsExactly(WEBP);
        }
    }

    @Test
    void extractText_traversalBetweenInvitationUuidsProtectsTheResolvedInvitation() {
        String targetId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String targetKey = WEBP.replace(INVITATION_ID, targetId);
        for (String parent : List.of("..", "%2e%2e", "%252e%252e")) {
            String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/" + parent + "/" + targetId + "/");

            assertThat(MediaReferences.extractText("https://cdn.example/" + alias))
                    .containsExactly(targetKey).doesNotContain(WEBP);
        }
    }

    @Test
    void extractText_resolvesTraversalOutOfAndBackIntoManagedNamespace() {
        String targetId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String targetKey = PNG.replace(INVITATION_ID, targetId);
        String alias = "https://cdn.example/invitations/" + INVITATION_ID + "/../../" + targetKey;

        assertThat(MediaReferences.extractText(alias)).containsExactly(targetKey).doesNotContain(PNG);
    }

    @Test
    void extractText_resolvesAliasesInsideCssAndHtmlWithoutMergingTokens() {
        String selfAlias = JPG.replace(INVITATION_ID + "/", INVITATION_ID + "/./");
        String parentAlias = PNG.replace(INVITATION_ID + "/", INVITATION_ID + "/unused/../");
        String text = "background:url('https://cdn.example/" + selfAlias + "?v=1#cover');"
                + "<img src=\"https://old.example/cache/" + parentAlias + "\">";

        assertThat(MediaReferences.extractText(text)).containsExactlyInAnyOrder(JPG, PNG);
    }

    @Test
    void extractText_treatsBackslashesAsSpecialHttpUrlPathSeparators() {
        String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/unused/../")
                .replace('/', '\\');

        assertThat(MediaReferences.extractText("https://cdn.example/" + alias)).containsExactly(WEBP);
        assertThat(MediaReferences.isManagedKey(alias)).isFalse();
    }

    @Test
    void extractText_removesLiteralTabCrLfBeforeScanningAndResolving() {
        String alias = WEBP.replace("invitations", "invi\tta\rti\nons")
                .replace("4677", "46\r\n77");
        String dotAlias = alias.replace(INVITATION_ID + "/", INVITATION_ID + "/unused/.\t./");

        assertThat(MediaReferences.extractText("https://cdn.example/" + alias)).containsExactly(WEBP);
        assertThat(MediaReferences.extractText("https://cdn.example/" + dotAlias)).containsExactly(WEBP);
        assertThat(MediaReferences.isManagedKey(alias)).isFalse();
    }

    @Test
    void extractText_combinesPercentEncodedBackslashesControlsAndDotSegments() {
        String alias = WEBP.replace("invitations/", "invi%09tations%5c")
                .replace(INVITATION_ID + "/", INVITATION_ID + "%5cunused%5c%252e%252e%5c");

        assertThat(MediaReferences.extractText("https://cdn.example/" + alias)).containsExactly(WEBP);
    }

    @Test
    void extractText_preservesExistingRawMatchesEvenWhenDotResolutionRemovesThem() {
        String alias = PNG.replace(INVITATION_ID + "/", INVITATION_ID + "/./");
        String text = "https://cdn.example/" + JPG + "/../not-an-image " + alias;

        assertThat(MediaReferences.extractText(text)).containsExactlyInAnyOrder(JPG, PNG);
    }

    @Test
    void extractText_doesNotLetQueryOrFragmentTraversalConsumeResolvedPath() {
        String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/./");

        assertThat(MediaReferences.extractText("https://cdn.example/" + alias + "?x=/../../#frag/.."))
                .containsExactly(WEBP);
    }

    @Test
    void extractText_preservesEmptySegmentsWhenResolvingParentTraversal() {
        String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "//../");

        assertThat(MediaReferences.extractText("https://cdn.example/" + alias)).containsExactly(WEBP);
    }

    @Test
    void extractText_keepsEncodedOrUrlPunctuationInsideRemovedSegments() {
        for (String removed : List.of("unused=x&y", "unused%20space", "unused%22quote", "unused%2520space")) {
            String alias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/" + removed + "/%252e%252e/");

            assertThat(MediaReferences.extractText("https://cdn.example/" + alias))
                    .as("removed path segment: %s", removed).containsExactly(WEBP);
        }
    }

    @Test
    void allKeys_scansNormalizedAliasesInVariantsFieldNamesAndTemplateThumbnails() {
        String variantAlias = JPG.replace(INVITATION_ID + "/", INVITATION_ID + "/unused/../");
        String fieldAlias = PNG.replace(INVITATION_ID + "/", INVITATION_ID + "/%252e/");
        String thumbnailAlias = WEBP.replace(INVITATION_ID + "/", INVITATION_ID + "/./");
        ObjectNode variants = MAPPER.createObjectNode().put("hidden", variantAlias);
        ObjectNode defaults = MAPPER.createObjectNode().put(fieldAlias, false);
        when(invitationRepository.findAll()).thenReturn(List.of(Invitation.builder()
                .status(InvitationStatus.ARCHIVED).langVariants(variants).build()));
        when(templateRepository.findAll()).thenReturn(List.of(Template.builder().defaultPageData(defaults)
                .thumbnailUrl("https://cdn.example/" + thumbnailAlias).build()));

        assertThat(references.allKeys()).containsExactlyInAnyOrder(JPG, PNG, WEBP);
        assertThat(variants.get("hidden").textValue()).isEqualTo(variantAlias);
        assertThat(defaults.has(fieldAlias)).isTrue();
        assertThat(MediaReferences.isManagedKey(variantAlias)).isFalse();
        assertThat(MediaReferences.isManagedKey(fieldAlias)).isFalse();
        assertThat(MediaReferences.isManagedKey(thumbnailAlias)).isFalse();
    }

    private static String key(int index, String extension) {
        return "invitations/" + INVITATION_ID + "/00112233-4455-4677-8899-"
                + "%012x".formatted(index) + "." + extension;
    }

    private static String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (char character : value.toCharArray()) {
            encoded.append("%%%02X".formatted((int) character));
        }
        return encoded.toString();
    }
}
