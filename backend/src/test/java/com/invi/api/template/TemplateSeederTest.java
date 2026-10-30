package com.invi.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.media.MediaLifecycle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateSeederTest {

    private static final List<String> CODES = List.of("classic", "minimal", "romantic", "modern", "garden", "hanji", "film", "letterpress", "seal");

    @Mock private TemplateRepository templateRepository;
    @Mock private MediaLifecycle lifecycle;

    private TemplateSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new TemplateSeeder(templateRepository, new ObjectMapper(), lifecycle);
    }

    @Test
    void emptyDatabase_createsEveryPresetWithItsCode() {
        List<Template> saved = seedFresh();

        assertThat(saved).extracting(Template::getCode).containsExactlyElementsOf(CODES);
        assertThat(saved).allSatisfy(t -> {
            assertThat(t.getCategory()).isEqualTo(t.getCode());
            assertThat(t.getDefaultPageData().get("blocks")).hasSize(7);
            assertThat(t.getDefaultPageData().at("/design/theme").asText()).isEqualTo(t.getCode());
            assertThat(t.getDefaultPageData().at("/design/backgroundColor").asText()).matches("#[0-9a-f]{6}");
        });
        assertThat(saved).extracting(Template::getName)
                .containsExactly("베이지 클래식", "미니멀 화이트", "로즈 레터", "모던 나이트",
                        "세이지 가든", "한지 담묵", "필름 포토", "네이비 레터프레스", "와인 실링 레터");
        InOrder order = inOrder(lifecycle, templateRepository);
        order.verify(lifecycle).lock();
        order.verify(templateRepository).findByCode("classic");
    }

    @Test
    void upToDateRows_areLeftUntouched() {
        Map<String, Template> existing = byCode(seedFresh());
        clearInvocations(templateRepository, lifecycle);
        when(templateRepository.findByCode(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(existing.get(inv.<String>getArgument(0))));

        seeder.run();

        verify(templateRepository, never()).save(any());
        verify(lifecycle, never()).referencesChanged(any(), any());
    }

    @Test
    void legacyRowWithoutCode_isClaimedInsteadOfDuplicated() {
        Template legacy = Template.builder().name("클래식").category("classic").build();
        when(templateRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(templateRepository.findFirstByCodeIsNullAndCategoryOrderByCreatedAtAsc(anyString()))
                .thenReturn(Optional.empty());
        when(templateRepository.findFirstByCodeIsNullAndCategoryOrderByCreatedAtAsc("classic"))
                .thenReturn(Optional.of(legacy));

        seeder.run();

        assertThat(legacy.getCode()).isEqualTo("classic");
        assertThat(legacy.getDefaultPageData().get("blocks")).hasSize(7);
        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository, times(CODES.size() - 1)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Template::getCode)
                .containsExactly("minimal", "romantic", "modern", "garden", "hanji", "film", "letterpress", "seal");
    }

    @Test
    void driftedRow_isRealignedToItsPreset() {
        Map<String, Template> existing = byCode(seedFresh());
        Template minimal = existing.get("minimal");
        minimal.syncPreset("minimal", "옛 이름", "old", null, null);
        clearInvocations(templateRepository, lifecycle);
        when(templateRepository.findByCode(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(existing.get(inv.<String>getArgument(0))));

        seeder.run();

        assertThat(minimal.getName()).isEqualTo("미니멀 화이트");
        assertThat(minimal.getCategory()).isEqualTo("minimal");
        assertThat(minimal.getDefaultPageData()).isNotNull();
        verify(templateRepository, never()).save(any());
        verify(lifecycle).referencesChanged(any(), any());
    }

    private List<Template> seedFresh() {
        when(templateRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(templateRepository.findFirstByCodeIsNullAndCategoryOrderByCreatedAtAsc(anyString()))
                .thenReturn(Optional.empty());
        seeder.run();
        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository, times(CODES.size())).save(captor.capture());
        return captor.getAllValues();
    }

    private static Map<String, Template> byCode(List<Template> templates) {
        return templates.stream().collect(Collectors.toMap(Template::getCode, Function.identity()));
    }
}
