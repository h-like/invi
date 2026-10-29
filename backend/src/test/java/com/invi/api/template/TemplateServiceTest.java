package com.invi.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.TestEntityIds;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(templateRepository);
    }

    @Test
    void findAll_mapsEntitiesToDtos() {
        Template classic = Template.builder().name("클래식").category("classic").build();
        when(templateRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(classic));

        List<TemplateDto> result = templateService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("클래식");
        assertThat(result.get(0).category()).isEqualTo("classic");
    }

    @Test
    void findById_returnsMetadataAndCompletePresetJson() throws Exception {
        UUID id = UUID.randomUUID();
        JsonNode preset = new ObjectMapper().readTree(
                """
                {
                  "blocks": [{"id":"hero-1","type":"hero","order":0,"visible":true,
                    "content":{"groomName":"샘플","brideName":"예시"},"style":{"padding":"lg"}}],
                  "design": {"theme":"classic","accentColor":"#b08d57"},
                  "custom": {"nested":[1,true,null]}
                }
                """);
        Template template = Template.builder()
                .name("클래식")
                .category("classic")
                .thumbnailUrl("https://example.com/classic.webp")
                .defaultPageData(preset)
                .build();
        TestEntityIds.setId(template, id);
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));

        TemplateDetailDto result = templateService.findById(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("클래식");
        assertThat(result.category()).isEqualTo("classic");
        assertThat(result.thumbnailUrl()).isEqualTo("https://example.com/classic.webp");
        assertThat(result.defaultPageData()).isEqualTo(preset.toString());
        assertThat(template.getDefaultPageData()).isSameAs(preset);
    }

    @Test
    void findById_allowsMissingPresetJson() {
        UUID id = UUID.randomUUID();
        Template template = Template.builder().name("클래식").category("classic").build();
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));

        assertThat(templateService.findById(id).defaultPageData()).isNull();
    }

    @Test
    void findById_throwsNotFound_whenTemplateMissing() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.findById(id)).isInstanceOf(NotFoundException.class);
    }
}
